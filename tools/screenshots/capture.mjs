#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..", "..");

const PREFERENCES_KEY = "terrascope.preferences.v1";

const USAGE = `Usage: tools/screenshots/capture.mjs [options]

Opens a running TerraScope in a real browser and saves screenshots of the main
screens. Run scripts/seed-demo.mjs first so the screens have something to show.

Options:
  --url <address>      web address (default: http://localhost:<WEB_PORT>)
  --out <folder>       where to save the images (default: docs/screenshots)
  --theme <name>       dark, light or both (default: dark). Dark images are named
                       <screen>.png and light ones <screen>-light.png
  --only <names>       comma separated list of screenshots to take
  --incident <text>    incident to open in the situation room (default: Izmir)
  --username <name>    sign-in name when sign-in is on
  --password <text>    password (or set TERRASCOPE_PASSWORD)
  --width <pixels>     browser width (default: 1440)
  --height <pixels>    browser height (default: 900)
  --scale <number>     device pixel ratio, 2 for sharper images (default: 1)
  --settle <ms>        pause after each screen settles (default: 700)
  --tile-wait <s>      how long to wait for map tiles (default: 30)
  --allow-blank-map    save map screens even if the map tiles did not load
  --channel <name>     use an installed browser such as chrome instead of Chromium
  --executable <path>  use a browser at this path
  --headed             show the browser while it works
  --list               print the screenshot names and exit
  -h, --help           show this text

Exit codes: 0 all saved, 1 could not run, 2 bad options, 3 some were skipped.`;

const SCREENS = [
  { name: "dashboard", map: true, note: "Map, hazard feed and statistics" },
  { name: "situation-room", note: "Incident list with the command overview" },
  { name: "incident-actions", note: "Response actions board" },
  { name: "incident-resources", note: "Resource requests and allocation" },
  { name: "incident-evacuation", note: "Evacuation zones and shelters" },
  { name: "incident-lifelines", note: "Critical lifeline assets" },
  { name: "incident-damage", note: "Damage assessment and recovery progress" },
  { name: "alerts", note: "Alert rules and triggered alerts" },
  { name: "simulations", note: "Impact simulation history" },
  { name: "regional-analysis", note: "Regional intelligence runs" },
  { name: "saved-events", note: "Saved events with priorities" },
  { name: "activity-log", note: "Who did what" },
  { name: "command-palette", note: "Command palette" },
  { name: "mobile", map: true, mobile: true, note: "Dashboard on a phone" },
];

class UsageError extends Error {}

function parseArguments(argv) {
  const options = {
    url: null,
    out: null,
    theme: "dark",
    only: null,
    incident: "Izmir",
    username: null,
    password: null,
    width: 1440,
    height: 900,
    scale: 1,
    settle: 700,
    tileWait: 30,
    allowBlankMap: false,
    channel: null,
    executable: null,
    headed: false,
    list: false,
    help: false,
  };

  const valued = new Map([
    ["--url", "url"],
    ["--out", "out"],
    ["--theme", "theme"],
    ["--only", "only"],
    ["--incident", "incident"],
    ["--username", "username"],
    ["--password", "password"],
    ["--width", "width"],
    ["--height", "height"],
    ["--scale", "scale"],
    ["--settle", "settle"],
    ["--tile-wait", "tileWait"],
    ["--channel", "channel"],
    ["--executable", "executable"],
  ]);

  const numeric = new Set(["width", "height", "scale", "settle", "tileWait"]);

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];

    if (argument === "-h" || argument === "--help") {
      options.help = true;
    } else if (argument === "--list") {
      options.list = true;
    } else if (argument === "--headed") {
      options.headed = true;
    } else if (argument === "--allow-blank-map") {
      options.allowBlankMap = true;
    } else if (valued.has(argument)) {
      const value = argv[index + 1];

      if (value === undefined || value.startsWith("--")) {
        throw new UsageError(`${argument} needs a value.`);
      }

      index += 1;

      const key = valued.get(argument);

      if (numeric.has(key)) {
        const parsed = Number(value);

        if (!Number.isFinite(parsed) || parsed <= 0) {
          throw new UsageError(`${argument} needs a positive number.`);
        }

        options[key] = parsed;
      } else {
        options[key] = value;
      }
    } else {
      throw new UsageError(`Unknown option: ${argument}`);
    }
  }

  if (!["dark", "light", "both"].includes(options.theme)) {
    throw new UsageError("--theme must be dark, light or both.");
  }

  if (options.only !== null) {
    const wanted = options.only.split(",").map((name) => name.trim()).filter(Boolean);
    const known = new Set(SCREENS.map((screen) => screen.name));
    const unknown = wanted.filter((name) => !known.has(name));

    if (unknown.length > 0) {
      throw new UsageError(
        `Unknown screenshot: ${unknown.join(", ")}. Use --list to see the names.`,
      );
    }

    options.only = new Set(wanted);
  }

  return options;
}

function readDotEnv() {
  const file = join(ROOT, ".env");
  const values = {};

  if (!existsSync(file)) {
    return values;
  }

  for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line);

    if (match) {
      values[match[1]] = match[2].trim().replace(/^(["'])(.*)\1$/, "$2");
    }
  }

  return values;
}

function pause(page, milliseconds) {
  return page.waitForTimeout(milliseconds);
}

async function blurFocus(page) {
  await page.evaluate(() => {
    const active = document.activeElement;

    if (active && active !== document.body && typeof active.blur === "function") {
      active.blur();
    }
  });
}

async function signInIfNeeded(page, credentials) {
  const password = page.locator('input[name="password"]');

  const gate = await password
    .first()
    .waitFor({ state: "visible", timeout: 4000 })
    .then(() => true)
    .catch(() => false);

  if (!gate) {
    return;
  }

  if (!credentials.username || !credentials.password) {
    throw new Error(
      "The app asks for a sign-in. Pass --username and --password, set TERRASCOPE_PASSWORD, or put the administrator account in .env.",
    );
  }

  await page.locator('input[name="username"]').fill(credentials.username);
  await password.first().fill(credentials.password);
  await page.locator('button[type="submit"]').click();

  const failed = await password
    .first()
    .waitFor({ state: "detached", timeout: 15000 })
    .then(() => false)
    .catch(() => true);

  if (failed) {
    throw new Error("Sign-in did not succeed. Check the username and password.");
  }
}

async function waitForDashboard(page) {
  await page.waitForSelector(".feed-panel", { timeout: 30000 });

  await page
    .waitForFunction(
      () => Number(document.querySelector(".event-count-badge")?.textContent) > 0,
      null,
      { timeout: 20000 },
    )
    .catch(() => undefined);
}

async function waitForTiles(page, seconds) {
  const loaded = await page
    .waitForFunction(
      () => {
        const tiles = document.querySelectorAll(".leaflet-tile");
        const done = document.querySelectorAll(".leaflet-tile-loaded");

        return tiles.length >= 4 && done.length === tiles.length;
      },
      null,
      { timeout: seconds * 1000, polling: 250 },
    )
    .then(() => true)
    .catch(() => false);

  if (loaded) {
    await page.waitForTimeout(600);
  }

  return loaded;
}

async function goTo(page, key) {
  await blurFocus(page);
  await page.keyboard.press("g");
  await page.keyboard.press(key);
}

async function closeOverlay(page, settle) {
  await page.keyboard.press("Escape");
  await pause(page, Math.min(settle, 400));
}

async function openSituationRoom(page, incidentText, settle) {
  await goTo(page, "c");
  await page.waitForSelector(".situation-incident-card", { timeout: 20000 });

  const wanted = page.locator(".situation-incident-card", { hasText: incidentText });

  const card = (await wanted.count()) > 0
    ? wanted.first()
    : page.locator(".situation-incident-card").first();

  await card.click();
  await pause(page, settle + 500);
}

async function scrollToSection(page, selector, settle) {
  const section = page.locator(selector).first();

  await section.waitFor({ state: "attached", timeout: 20000 });

  await section.evaluate((element) => {
    element.scrollIntoView({ block: "start" });

    for (let parent = element.parentElement; parent; parent = parent.parentElement) {
      const overflow = window.getComputedStyle(parent).overflowY;

      if ((overflow === "auto" || overflow === "scroll") && parent.scrollHeight > parent.clientHeight) {
        parent.scrollTop = Math.max(0, parent.scrollTop - 150);
        break;
      }
    }
  });

  await pause(page, settle);
}

async function openIncidentBoard(page, context, tab, selector) {
  await openSituationRoom(page, context.options.incident, context.options.settle);
  await openTab(page, tab, context.options.settle);
  await scrollToSection(page, selector, context.options.settle);
}

async function openTab(page, label, settle) {
  await page.getByRole("tab", { name: label }).first().click();
  await pause(page, settle + 300);
}

const SHOOTS = {
  dashboard: async () => undefined,

  "situation-room": async (page, context) => {
    await openSituationRoom(page, context.options.incident, context.options.settle);
  },

  "incident-actions": (page, context) =>
    openIncidentBoard(page, context, /Operations/, ".response-action-list"),

  "incident-resources": (page, context) =>
    openIncidentBoard(page, context, /Operations/, ".resource-list"),

  "incident-evacuation": (page, context) =>
    openIncidentBoard(page, context, /Operations/, ".evacuation-section-heading"),

  "incident-lifelines": (page, context) =>
    openIncidentBoard(page, context, /Operations/, ".lifeline-list"),

  "incident-damage": (page, context) =>
    openIncidentBoard(page, context, /Recovery/, ".damage-list"),

  alerts: async (page, context) => {
    await goTo(page, "a");
    await pause(page, context.options.settle + 500);
  },

  simulations: async (page, context) => {
    await goTo(page, "s");
    await pause(page, context.options.settle + 500);
  },

  "regional-analysis": async (page, context) => {
    await goTo(page, "i");
    await pause(page, context.options.settle + 500);
  },

  "saved-events": async (page, context) => {
    await goTo(page, "w");
    await pause(page, context.options.settle + 500);
  },

  "activity-log": async (page, context) => {
    await goTo(page, "l");
    await pause(page, context.options.settle + 500);
  },

  "command-palette": async (page, context) => {
    await blurFocus(page);
    await page.keyboard.press("Control+k");
    await page.waitForSelector(".palette", { timeout: 10000 });
    await pause(page, context.options.settle);
  },

  mobile: async () => undefined,
};

async function newPage(browser, options, theme, mobile, hooks) {
  const viewport = mobile
    ? { width: 390, height: 844 }
    : { width: options.width, height: options.height };

  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: options.scale,
    colorScheme: theme === "light" ? "light" : "dark",
  });

  await context.addInitScript(
    ({ key, value }) => {
      try {
        window.localStorage.setItem(key, JSON.stringify({ theme: value }));
      } catch {
        return;
      }
    },
    { key: PREFERENCES_KEY, value: theme },
  );

  if (hooks.prepareContext) {
    await hooks.prepareContext(context);
  }

  return { context, page: await context.newPage() };
}

async function loadApp(page, address, credentials, options) {
  try {
    await page.goto(address, { waitUntil: "domcontentloaded", timeout: 45000 });
  } catch (error) {
    const reason = String(error.message).split("\n")[0].replace(/^page\.goto:\s*/, "");

    throw new Error(
      `Could not open ${address}: ${reason}\nIs TerraScope running? Start it with bash scripts/start.sh or pass --url.`,
    );
  }

  await signInIfNeeded(page, credentials);
  await waitForDashboard(page);
  await pause(page, options.settle);
}

function fileFor(folder, name, theme) {
  return join(folder, theme === "light" ? `${name}-light.png` : `${name}.png`);
}

export async function capture(options, hooks = {}) {
  const env = readDotEnv();

  const address =
    options.url ?? `http://localhost:${env.WEB_PORT || "3000"}`;

  const folder = resolve(process.cwd(), options.out ?? join(ROOT, "docs", "screenshots"));

  const credentials = {
    username: options.username ?? process.env.TERRASCOPE_USERNAME ?? env.TERRASCOPE_SECURITY_ADMIN_USERNAME,
    password: options.password ?? process.env.TERRASCOPE_PASSWORD ?? env.TERRASCOPE_SECURITY_ADMIN_PASSWORD,
  };

  const themes = options.theme === "both" ? ["dark", "light"] : [options.theme];

  const screens = SCREENS.filter(
    (screen) => options.only === null || options.only.has(screen.name),
  );

  let playwright;

  try {
    playwright = await import("playwright");
  } catch {
    throw new Error(
      "Playwright is not installed. Run bash scripts/screenshots.sh, which installs it.",
    );
  }

  mkdirSync(folder, { recursive: true });

  const launch = { headless: !options.headed };

  if (options.channel) {
    launch.channel = options.channel;
  }

  if (options.executable) {
    launch.executablePath = options.executable;
  }

  const browser = await playwright.chromium.launch(launch);

  const saved = [];
  const skipped = [];

  try {
    for (const theme of themes) {
      console.log(`\n${theme} theme`);

      for (const mobile of [false, true]) {
        const group = screens.filter((screen) => Boolean(screen.mobile) === mobile);

        if (group.length === 0) {
          continue;
        }

        const { context, page } = await newPage(browser, options, theme, mobile, hooks);

        const failures = [];

        page.on("pageerror", (error) => failures.push(error.message));

        try {
          await loadApp(page, address, credentials, options);

          const tilesReady = await waitForTiles(page, options.tileWait);

          for (const screen of group) {
            const target = fileFor(folder, screen.name, theme);

            if (screen.map && !tilesReady && !options.allowBlankMap) {
              skipped.push(`${screen.name} (${theme}): map tiles did not load`);
              console.log(`  skipped ${screen.name}: the map tiles did not load`);
              continue;
            }

            try {
              await SHOOTS[screen.name](page, { options });
              await pause(page, options.settle);
              await page.screenshot({ path: target });
              saved.push(target);
              console.log(`  saved   ${screen.name}`);
            } catch (error) {
              skipped.push(`${screen.name} (${theme}): ${error.message.split("\n")[0]}`);
              console.log(`  skipped ${screen.name}: ${error.message.split("\n")[0]}`);
            }

            if (!mobile) {
              await closeOverlay(page, options.settle);
              await closeOverlay(page, options.settle);
            }
          }

          if (failures.length > 0) {
            console.log(`  note: the page reported ${failures.length} script error(s)`);
          }
        } finally {
          await context.close();
        }
      }
    }
  } finally {
    await browser.close();
  }

  return { saved, skipped, folder };
}

async function main() {
  let options;

  try {
    options = parseArguments(process.argv.slice(2));
  } catch (error) {
    if (error instanceof UsageError) {
      console.error(`${error.message}\n\n${USAGE}`);
      return 2;
    }

    throw error;
  }

  if (options.help) {
    console.log(USAGE);
    return 0;
  }

  if (options.list) {
    for (const screen of SCREENS) {
      console.log(`${screen.name.padEnd(22)}${screen.note}`);
    }

    return 0;
  }

  try {
    const result = await capture(options);

    console.log(`\nSaved ${result.saved.length} image(s) to ${result.folder}`);

    if (result.skipped.length > 0) {
      console.log(`\nSkipped ${result.skipped.length}:`);

      for (const line of result.skipped) {
        console.log(`  ${line}`);
      }

      return 3;
    }

    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    return 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().then((code) => {
    process.exitCode = code;
  });
}
