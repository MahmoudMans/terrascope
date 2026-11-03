import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const SCOPE = 'html[data-theme="light"]';

const COLOR_PATTERN =
  /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})(?![0-9a-zA-Z])|rgba?\(\s*[^()]*\)/g;

const SHADOW_PROPERTIES = new Set([
  "box-shadow",
  "text-shadow",
  "filter",
  "drop-shadow",
]);

const isShadow = (property) =>
  SHADOW_PROPERTIES.has(property) || property.includes("shadow");

const smoothstep = (low, high, value) => {
  const t = clamp((value - low) / (high - low), 0, 1);

  return t * t * (3 - 2 * t);
};

const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

const toLinear = (channel) =>
  channel <= 0.04045
    ? channel / 12.92
    : ((channel + 0.055) / 1.055) ** 2.4;

const fromLinear = (channel) =>
  channel <= 0.0031308
    ? 12.92 * channel
    : 1.055 * channel ** (1 / 2.4) - 0.055;

export function toOklch([red, green, blue]) {
  const r = toLinear(red / 255);
  const g = toLinear(green / 255);
  const b = toLinear(blue / 255);

  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);

  const lightness = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const bb = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

  return [lightness, Math.hypot(a, bb), Math.atan2(bb, a)];
}

function fromOklch([lightness, chroma, hue]) {
  const a = chroma * Math.cos(hue);
  const b = chroma * Math.sin(hue);

  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3;

  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

const inGamut = (channels) =>
  channels.every((channel) => channel >= -0.0005 && channel <= 1.0005);

export function relativeLuminance([red, green, blue]) {
  return (
    0.2126 * toLinear(red / 255)
    + 0.7152 * toLinear(green / 255)
    + 0.0722 * toLinear(blue / 255)
  );
}

export function contrastRatio(first, second) {
  const high = Math.max(relativeLuminance(first), relativeLuminance(second));
  const low = Math.min(relativeLuminance(first), relativeLuminance(second));

  return (high + 0.05) / (low + 0.05);
}

export function mapLightness(lightness, chroma = 0) {
  const plain = clamp(1.113 - 0.925 * lightness, 0, 1);
  const vivid = clamp(0.4 + 0.5 * (1 - lightness), 0, 1);
  const weight = smoothstep(0.03, 0.1, chroma);

  return plain * (1 - weight) + vivid * weight;
}

function rgbFromOklch(lightness, chroma, hue) {
  let scale = 1;
  let linear = fromOklch([lightness, chroma, hue]);

  while (!inGamut(linear) && scale > 0.01) {
    scale *= 0.94;
    linear = fromOklch([lightness, chroma * scale, hue]);
  }

  return linear.map((channel) =>
    Math.round(clamp(fromLinear(clamp(channel, 0, 1)), 0, 1) * 255),
  );
}

export function lightenRgb(rgb) {
  const [original, chroma, hue] = toOklch(rgb);

  return rgbFromOklch(mapLightness(original, chroma), chroma, hue);
}

const DARK_BACKDROP = [16, 30, 34];
const READABLE_FROM = 3;
const READABLE_TARGET = 5;
const TEXT_TOKENS = /^--(?:text|accent|blue|green|yellow|orange|red)(?:-[a-z]+)?$/;

const isTextual = (property) =>
  property === "color" || TEXT_TOKENS.test(property);

export function keepReadable(original, converted) {
  if (contrastRatio(original, DARK_BACKDROP) < READABLE_FROM) {
    return converted;
  }

  const backdrop = lightenRgb(DARK_BACKDROP);

  if (contrastRatio(converted, backdrop) >= READABLE_TARGET) {
    return converted;
  }

  const [lightness, chroma, hue] = toOklch(converted);

  let next = converted;
  let step = lightness;

  while (contrastRatio(next, backdrop) < READABLE_TARGET && step > 0.05) {
    step -= 0.005;
    next = rgbFromOklch(step, chroma, hue);
  }

  return next;
}

function parseColor(text) {
  if (text.startsWith("#")) {
    let digits = text.slice(1);

    if (digits.length <= 4) {
      digits = [...digits].map((digit) => digit + digit).join("");
    }

    const channels = [0, 2, 4].map((offset) =>
      Number.parseInt(digits.slice(offset, offset + 2), 16),
    );

    const alpha =
      digits.length === 8
        ? Number.parseInt(digits.slice(6, 8), 16) / 255
        : 1;

    return { rgb: channels, alpha, hex: true };
  }

  const parts = text
    .slice(text.indexOf("(") + 1, -1)
    .trim()
    .split(/[\s,/]+/)
    .filter((part) => part.length > 0);

  if (parts.length < 3 || parts.some((part) => part.includes("var("))) {
    return null;
  }

  const channels = parts.slice(0, 3).map((part) =>
    part.endsWith("%")
      ? Math.round((Number.parseFloat(part) / 100) * 255)
      : Number.parseFloat(part),
  );

  let alpha = 1;

  if (parts.length > 3) {
    alpha = parts[3].endsWith("%")
      ? Number.parseFloat(parts[3]) / 100
      : Number.parseFloat(parts[3]);
  }

  if (channels.some(Number.isNaN) || Number.isNaN(alpha)) {
    return null;
  }

  return { rgb: channels, alpha, hex: false };
}

function format({ rgb, alpha, hex }) {
  if (hex && alpha >= 0.999) {
    return `#${rgb.map((channel) => channel.toString(16).padStart(2, "0")).join("")}`;
  }

  const rounded = Math.round(alpha * 1000) / 1000;

  return `rgba(${rgb.join(", ")}, ${rounded})`;
}

export function convertColor(text, property) {
  const parsed = parseColor(text);

  if (parsed === null) {
    return text;
  }

  const lightness = toOklch(parsed.rgb)[0];

  if (isShadow(property)) {
    if (lightness < 0.34) {
      return format({
        rgb: [14, 36, 42],
        alpha: parsed.alpha * 0.42,
        hex: false,
      });
    }

    return format({
      rgb: lightenRgb(parsed.rgb),
      alpha: parsed.alpha * 0.7,
      hex: false,
    });
  }

  const lightened = lightenRgb(parsed.rgb);

  return format({
    rgb:
      isTextual(property) && parsed.alpha >= 0.9
        ? keepReadable(parsed.rgb, lightened)
        : lightened,
    alpha: parsed.alpha,
    hex: parsed.hex,
  });
}

export function convertValue(value, property) {
  if (property === "color-scheme") {
    return value.replace(/\bdark\b/g, "light");
  }

  return value.replace(COLOR_PATTERN, (match) => convertColor(match, property));
}

function hasColor(value) {
  COLOR_PATTERN.lastIndex = 0;

  const found = COLOR_PATTERN.test(value);

  COLOR_PATTERN.lastIndex = 0;

  return found;
}

function skipString(css, index) {
  const quote = css[index];

  let cursor = index + 1;

  while (cursor < css.length && css[cursor] !== quote) {
    cursor += css[cursor] === "\\" ? 2 : 1;
  }

  return cursor + 1;
}

function findClose(css, from) {
  let depth = 1;
  let cursor = from;

  while (cursor < css.length) {
    const character = css[cursor];

    if (css.startsWith("/*", cursor)) {
      const end = css.indexOf("*/", cursor + 2);

      cursor = end < 0 ? css.length : end + 2;
    } else if (character === '"' || character === "'") {
      cursor = skipString(css, cursor);
    } else {
      if (character === "{") {
        depth += 1;
      } else if (character === "}") {
        depth -= 1;

        if (depth === 0) {
          return cursor;
        }
      }

      cursor += 1;
    }
  }

  throw new Error("Unbalanced braces in the stylesheet.");
}

function stripComments(css) {
  return css.replace(/\/\*[\s\S]*?\*\//g, "");
}

function splitTopLevel(text, separator) {
  const parts = [];

  let depth = 0;
  let current = "";
  let cursor = 0;

  while (cursor < text.length) {
    const character = text[cursor];

    if (character === '"' || character === "'") {
      const end = skipString(text, cursor);

      current += text.slice(cursor, end);
      cursor = end;
      continue;
    }

    if (character === "(") {
      depth += 1;
    } else if (character === ")") {
      depth -= 1;
    }

    if (character === separator && depth === 0) {
      parts.push(current);
      current = "";
    } else {
      current += character;
    }

    cursor += 1;
  }

  parts.push(current);

  return parts;
}

function parseDeclarations(body) {
  if (body.includes("{")) {
    throw new Error(
      "Nested rules are not supported by the light theme generator.",
    );
  }

  return splitTopLevel(body, ";")
    .map((text) => text.trim())
    .filter((text) => text.length > 0)
    .map((text) => {
      const colon = text.indexOf(":");

      return {
        property: text.slice(0, colon).trim(),
        value: text.slice(colon + 1).trim(),
      };
    });
}

function parseNodes(css) {
  const nodes = [];

  let cursor = 0;
  let prelude = "";

  while (cursor < css.length) {
    const character = css[cursor];

    if (character === '"' || character === "'") {
      const end = skipString(css, cursor);

      prelude += css.slice(cursor, end);
      cursor = end;
    } else if (character === "{") {
      const close = findClose(css, cursor + 1);
      const head = prelude.trim();
      const body = css.slice(cursor + 1, close);

      prelude = "";
      cursor = close + 1;

      if (head.startsWith("@keyframes")) {
        nodes.push({
          type: "keyframes",
          name: head.slice("@keyframes".length).trim(),
          frames: parseNodes(body),
        });
      } else if (head.startsWith("@media") || head.startsWith("@supports")) {
        nodes.push({
          type: "group",
          head,
          children: parseNodes(body),
        });
      } else if (head.startsWith("@")) {
        nodes.push({ type: "other" });
      } else {
        nodes.push({
          type: "rule",
          selectors: splitTopLevel(head, ",").map((text) => text.trim()),
          declarations: parseDeclarations(body),
        });
      }
    } else if (character === ";") {
      prelude = "";
      cursor += 1;
    } else {
      prelude += character;
      cursor += 1;
    }
  }

  return nodes;
}

function scopeSelector(selector) {
  if (selector === ":root" || selector === "html") {
    return SCOPE;
  }

  if (selector.startsWith(":root")) {
    return SCOPE + selector.slice(":root".length);
  }

  if (/^html(?![a-zA-Z0-9_-])/.test(selector)) {
    return SCOPE + selector.slice("html".length);
  }

  return `${SCOPE} ${selector}`;
}

function renameKeyframe(value, renamed) {
  let next = value;

  for (const name of renamed) {
    next = next.replace(
      new RegExp(`(^|[\\s,])${name}(?=[\\s,;]|$)`, "g"),
      `$1${name}--light`,
    );
  }

  return next;
}

const ANIMATION_PROPERTIES = new Set(["animation", "animation-name"]);

const MIRRORED_PROPERTIES =
  /^(?:color|background|background-color|background-image|border(?:-(?:top|right|bottom|left|block|inline)(?:-(?:start|end))?)?(?:-color)?|outline|outline-color|fill|stroke|box-shadow|text-shadow|caret-color|text-decoration|text-decoration-color|accent-color|column-rule|column-rule-color)$/;

function emitDeclarations(declarations, renamed, verbatim = false) {
  const lines = [];

  for (const { property, value } of declarations) {
    if (verbatim) {
      if (
        hasColor(value)
        || property === "color-scheme"
        || MIRRORED_PROPERTIES.test(property)
      ) {
        lines.push(`  ${property}: ${value};`);
      }

      continue;
    }

    const animated =
      ANIMATION_PROPERTIES.has(property)
      && renameKeyframe(value, renamed) !== value;

    if (animated) {
      lines.push(`  ${property}: ${renameKeyframe(value, renamed)};`);
    } else if (hasColor(value) || property === "color-scheme") {
      lines.push(`  ${property}: ${convertValue(value, property)};`);
    } else if (MIRRORED_PROPERTIES.test(property)) {
      lines.push(`  ${property}: ${value};`);
    }
  }

  return lines;
}

function emitNodes(nodes, renamed, indent = "", verbatim = false) {
  const blocks = [];

  for (const node of nodes) {
    if (node.type === "rule") {
      const lines = emitDeclarations(node.declarations, renamed, verbatim);

      if (lines.length > 0) {
        const selector = node.selectors.map(scopeSelector).join(",\n");

        blocks.push(`${selector} {\n${lines.join("\n")}\n}`);
      }
    } else if (node.type === "group") {
      const inner = emitNodes(
        node.children,
        renamed,
        "  ",
        verbatim || /\bprint\b/.test(node.head),
      );

      if (inner.length > 0) {
        blocks.push(`${node.head} {\n${inner.join("\n\n")}\n}`);
      }
    }
  }

  return blocks.map((block) =>
    indent === ""
      ? block
      : block
          .split("\n")
          .map((line) => indent + line)
          .join("\n"),
  );
}

function emitKeyframes(nodes) {
  const blocks = [];
  const renamed = [];

  for (const node of nodes) {
    if (node.type !== "keyframes") {
      continue;
    }

    const frames = node.frames
      .filter((frame) => frame.type === "rule")
      .map((frame) => ({
        selector: frame.selectors.join(", "),
        lines: frame.declarations.map(
          ({ property, value }) =>
            `    ${property}: ${hasColor(value) ? convertValue(value, property) : value};`,
        ),
        colored: frame.declarations.some(({ value }) => hasColor(value)),
      }));

    if (frames.some((frame) => frame.colored)) {
      renamed.push(node.name);

      blocks.push(
        `@keyframes ${node.name}--light {\n${frames
          .map((frame) => `  ${frame.selector} {\n${frame.lines.join("\n")}\n  }`)
          .join("\n")}\n}`,
      );
    }
  }

  return { blocks, renamed };
}

const REFINEMENTS = `${SCOPE} .leaflet-tile-pane {
  filter: saturate(0.85) brightness(1.02);
}`;

export function generate(css) {
  const nodes = parseNodes(stripComments(css));
  const keyframes = emitKeyframes(nodes);
  const rules = emitNodes(nodes, keyframes.renamed);

  return (
    [...keyframes.blocks, ...rules, REFINEMENTS].join("\n\n") + "\n"
  );
}

function main() {
  const here = path.dirname(fileURLToPath(import.meta.url));
  const source = path.join(here, "..", "src", "app", "globals.css");
  const target = path.join(here, "..", "src", "app", "light-theme.generated.css");

  if (!existsSync(source)) {
    throw new Error(`Cannot find ${source}`);
  }

  const output = generate(readFileSync(source, "utf8"));

  if (!existsSync(target) || readFileSync(target, "utf8") !== output) {
    writeFileSync(target, output);

    console.log(`Light theme written (${output.split("\n").length} lines).`);
  } else {
    console.log("Light theme is up to date.");
  }
}

if (
  process.argv[1] !== undefined
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
) {
  main();
}
