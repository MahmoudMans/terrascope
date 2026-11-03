export type ThemeChoice = "dark" | "light" | "system";

export type ResolvedTheme = "dark" | "light";

export const THEME_CHOICES: Array<{
  value: ThemeChoice;
  label: string;
}> = [
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "system", label: "System" },
];

const LIGHT_QUERY = "(prefers-color-scheme: light)";

function systemPrefersLight(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }

  return window.matchMedia(LIGHT_QUERY).matches;
}

export function resolveTheme(choice: ThemeChoice): ResolvedTheme {
  if (choice === "system") {
    return systemPrefersLight() ? "light" : "dark";
  }

  return choice;
}

export function applyTheme(choice: ThemeChoice): void {
  const root = document.documentElement;

  root.dataset.theme = resolveTheme(choice);
  root.dataset.themeChoice = choice;
}

export function watchSystemTheme(onChange: () => void): () => void {
  if (typeof window.matchMedia !== "function") {
    return () => undefined;
  }

  const query = window.matchMedia(LIGHT_QUERY);

  query.addEventListener("change", onChange);

  return () => {
    query.removeEventListener("change", onChange);
  };
}

export function watchPrintTheme(): () => void {
  const handleBeforePrint = () => {
    document.documentElement.dataset.theme = "light";
  };

  const handleAfterPrint = () => {
    const choice = document.documentElement.dataset.themeChoice;

    applyTheme(
      choice === "light" || choice === "system" ? choice : "dark",
    );
  };

  window.addEventListener("beforeprint", handleBeforePrint);
  window.addEventListener("afterprint", handleAfterPrint);

  return () => {
    window.removeEventListener("beforeprint", handleBeforePrint);
    window.removeEventListener("afterprint", handleAfterPrint);
  };
}

export function oppositeTheme(current: ResolvedTheme): ThemeChoice {
  return current === "light" ? "dark" : "light";
}

export const THEME_INIT_SCRIPT = [
  "(function(){",
  "var root=document.documentElement;",
  "try{",
  "var choice='dark';",
  "var stored=window.localStorage.getItem('terrascope.preferences.v1');",
  "if(stored){var theme=JSON.parse(stored).theme;",
  "if(theme==='light'||theme==='dark'||theme==='system'){choice=theme;}}",
  "var light=choice==='light'||(choice==='system'&&window.matchMedia('(prefers-color-scheme: light)').matches);",
  "root.dataset.theme=light?'light':'dark';",
  "root.dataset.themeChoice=choice;",
  "}catch(error){root.dataset.theme='dark';}",
  "})();",
].join("");
