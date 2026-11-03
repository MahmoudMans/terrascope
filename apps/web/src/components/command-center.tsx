"use client";

import {
  Activity,
  Bookmark,
  BellRing,
  ClipboardList,
  Clock3,
  CloudLightning,
  CornerDownLeft,
  Download,
  Flame,
  Globe2,
  Keyboard,
  Moon,
  Mountain,
  Radar,
  RefreshCw,
  Search,
  Settings,
  Siren,
  Sparkles,
  Sun,
  X,
  type LucideIcon,
} from "lucide-react";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type KeyboardEvent as ReactKeyboardEvent,
} from "react";

import { createPortal } from "react-dom";

import {
  COMMAND_GROUPS,
  COMMANDS,
  filterCommands,
  normalizeKey,
  resolveShortcut,
  type CommandDefinition,
  type CommandId,
} from "@/lib/commands";

import { requestActivityLog } from "@/lib/command-events";

import { useDialogBehavior } from "@/lib/use-dialog-behavior";

import { useSession } from "@/lib/use-session";

const ICONS: Record<CommandId, LucideIcon> = {
  "go-timeline": Clock3,
  "go-regions": Globe2,
  "go-simulations": Sparkles,
  "go-regional": Radar,
  "go-alerts": BellRing,
  "go-situation": Siren,
  "go-saved": Bookmark,
  "go-activity": ClipboardList,
  refresh: RefreshCw,
  search: Search,
  "toggle-theme": Sun,
  settings: Settings,
  "export-feed-csv": Download,
  "export-feed-json": Download,
  "feed-earthquakes": Activity,
  "feed-volcanoes": Mountain,
  "feed-wildfires": Flame,
  "feed-storms": CloudLightning,
  shortcuts: Keyboard,
};

const SEQUENCE_WINDOW_MS = 1500;

export type CommandRunners = Partial<Record<CommandId, () => void>>;

type CommandCenterProps = {
  runners: CommandRunners;
  shortcutsEnabled: boolean;
};

type PaletteItem = CommandDefinition & {
  icon: LucideIcon;
};

function subscribeNothing(): () => void {
  return () => undefined;
}

function usesMacKeys(): boolean {
  return /Mac|iPhone|iPad/i.test(navigator.userAgent);
}

function useModifierLabel(): string {
  const mac = useSyncExternalStore(subscribeNothing, usesMacKeys, () => false);

  return mac ? "⌘" : "Ctrl";
}

function isTypingTarget(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement
    && target.closest(
      "input, textarea, select, [contenteditable=''], [contenteditable='true']",
    ) !== null
  );
}

function modalIsOpen(): boolean {
  return document.querySelector("[aria-modal='true']") !== null;
}

function currentTheme(): "dark" | "light" {
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

function personalize(commands: CommandDefinition[]): PaletteItem[] {
  const toLight = currentTheme() === "dark";

  return commands.map((command) => {
    if (command.id !== "toggle-theme") {
      return { ...command, icon: ICONS[command.id] };
    }

    return {
      ...command,
      title: toLight ? "Switch to light theme" : "Switch to dark theme",
      icon: toLight ? Sun : Moon,
    };
  });
}

function KeyHint({ keys, className }: { keys: string[]; className?: string }) {
  return (
    <span className={className ?? "key-hint"}>
      <kbd className="kbd">{keys[0].toUpperCase()}</kbd>

      {keys.length > 1 && (
        <>
          <span className="key-hint__then">then</span>

          <kbd className="kbd">{keys[1].toUpperCase()}</kbd>
        </>
      )}
    </span>
  );
}

type PaletteProps = {
  commands: CommandDefinition[];
  modifier: string;
  onRun: (id: CommandId) => void;
  onClose: () => void;
};

function Palette({ commands, modifier, onRun, onClose }: PaletteProps) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const listRef = useRef<HTMLUListElement | null>(null);

  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);

  useDialogBehavior({ open: true, onClose, containerRef: dialogRef });

  useEffect(() => {
    inputRef.current?.focus({ preventScroll: true });
  }, []);

  const items = useMemo(() => personalize(commands), [commands]);

  const results = useMemo(() => filterCommands(query, items), [query, items]);

  const positions = useMemo(
    () => new Map(results.map((item, index) => [item.id, index])),
    [results],
  );

  const active = results.length === 0 ? -1 : Math.min(activeIndex, results.length - 1);

  useEffect(() => {
    if (active < 0) {
      return;
    }

    listRef.current
      ?.querySelector<HTMLElement>(`[data-index='${active}']`)
      ?.scrollIntoView({ block: "nearest" });
  }, [active]);

  const grouped = useMemo(() => {
    if (query.trim() !== "") {
      return [{ group: null, entries: results }];
    }

    return COMMAND_GROUPS.map((group) => ({
      group,
      entries: results.filter((item) => item.group === group),
    })).filter((section) => section.entries.length > 0);
  }, [query, results]);

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing) {
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex(results.length === 0 ? 0 : (active + 1) % results.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex(
        results.length === 0 ? 0 : (active - 1 + results.length) % results.length,
      );
    } else if (event.key === "Home") {
      event.preventDefault();
      setActiveIndex(0);
    } else if (event.key === "End") {
      event.preventDefault();
      setActiveIndex(Math.max(0, results.length - 1));
    } else if (event.key === "Enter") {
      event.preventDefault();

      if (active >= 0) {
        onRun(results[active].id);
      }
    }
  };

  return createPortal(
    <div className="dialog-overlay dialog-overlay--top">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close command palette"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="palette"
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
        tabIndex={-1}
      >
        <div className="palette__search">
          <Search size={17} aria-hidden="true" />

          <input
            ref={inputRef}
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls="palette-results"
            aria-autocomplete="list"
            aria-activedescendant={active >= 0 ? `palette-option-${active}` : undefined}
            aria-label="Search commands"
            placeholder="Type a command or search…"
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={handleKeyDown}
          />

          <button
            type="button"
            className="palette__close"
            aria-label="Close"
            onClick={onClose}
          >
            <X size={16} aria-hidden="true" />
          </button>
        </div>

        {results.length === 0 ? (
          <p className="palette__empty" role="status">
            No commands match “{query.trim()}”.
          </p>
        ) : (
          <ul
            ref={listRef}
            id="palette-results"
            className="palette__list"
            role="listbox"
            aria-label="Commands"
          >
            {grouped.map((section) => (
              <li
                key={section.group ?? "results"}
                role="presentation"
                className="palette__section"
              >
                {section.group !== null && (
                  <div className="palette__group" aria-hidden="true">
                    {section.group}
                  </div>
                )}

                <ul
                  role={section.group === null ? "presentation" : "group"}
                  aria-label={section.group ?? undefined}
                  className="palette__options"
                >
                  {section.entries.map((item) => {
                    const index = positions.get(item.id) ?? 0;
                    const Icon = item.icon;

                    return (
                      <li
                        key={item.id}
                        id={`palette-option-${index}`}
                        data-index={index}
                        role="option"
                        aria-selected={index === active}
                        className="palette__item"
                        onMouseMove={() => {
                          if (index !== active) {
                            setActiveIndex(index);
                          }
                        }}
                        onClick={() => onRun(item.id)}
                      >
                        <Icon size={16} aria-hidden="true" />

                        <span>{item.title}</span>

                        {item.keys !== undefined && (
                          <KeyHint keys={item.keys} className="key-hint palette__keys" />
                        )}
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>
        )}

        <footer className="palette__footer">
          <span>
            <kbd className="kbd">↑</kbd>
            <kbd className="kbd">↓</kbd>
            Move
          </span>

          <span>
            <kbd className="kbd">
              <CornerDownLeft size={11} aria-hidden="true" />
            </kbd>
            Run
          </span>

          <span>
            <kbd className="kbd">Esc</kbd>
            Close
          </span>

          <span className="palette__footer-end">
            <kbd className="kbd">{modifier}</kbd>
            <kbd className="kbd">K</kbd>
            Toggle
          </span>
        </footer>

        <p className="visually-hidden" role="status">
          {results.length === 1 ? "1 command" : `${results.length} commands`}
        </p>
      </section>
    </div>,
    document.body,
  );
}

type ShortcutsHelpProps = {
  commands: CommandDefinition[];
  modifier: string;
  enabled: boolean;
  onClose: () => void;
};

function ShortcutsHelp({ commands, modifier, enabled, onClose }: ShortcutsHelpProps) {
  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open: true, onClose, containerRef: dialogRef });

  const items = useMemo(() => personalize(commands), [commands]);

  return createPortal(
    <div className="dialog-overlay">
      <button
        type="button"
        className="drawer-backdrop"
        aria-label="Close dialog"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="auth-dialog auth-dialog--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcuts-title"
        tabIndex={-1}
      >
        <header className="auth-dialog__header">
          <div>
            <span className="panel-kicker">KEYBOARD</span>

            <h2 id="shortcuts-title">Keyboard shortcuts</h2>
          </div>

          <button
            type="button"
            className="icon-button"
            aria-label="Close"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="auth-dialog__body">
          {!enabled && (
            <p className="auth-dialog__hint">
              Single-key shortcuts are turned off. Turn them on in Settings under
              Display. The command palette always works.
            </p>
          )}

          <div className="shortcut-groups">
            <section className="shortcut-group">
              <h3>Command palette</h3>

              <ul>
                <li className="shortcut-row">
                  <span>Open or close the command palette</span>

                  <span className="key-hint">
                    <kbd className="kbd">{modifier}</kbd>
                    <kbd className="kbd">K</kbd>
                  </span>
                </li>
              </ul>
            </section>

            {COMMAND_GROUPS.map((group) => {
              const entries = items.filter(
                (item) => item.group === group && item.keys !== undefined,
              );

              if (entries.length === 0) {
                return null;
              }

              return (
                <section key={group} className="shortcut-group">
                  <h3>{group}</h3>

                  <ul>
                    {entries.map((item) => (
                      <li key={item.id} className="shortcut-row">
                        <span>{item.title}</span>

                        <KeyHint keys={item.keys ?? []} />
                      </li>
                    ))}
                  </ul>
                </section>
              );
            })}
          </div>
        </div>
      </section>
    </div>,
    document.body,
  );
}

export function CommandCenter({ runners, shortcutsEnabled }: CommandCenterProps) {
  const session = useSession();
  const modifier = useModifierLabel();

  const [paletteOpen, setPaletteOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [pending, setPending] = useState(false);

  const canSeeActivity = session === null || session.user.role !== "VIEWER";

  const available = useMemo(
    () =>
      COMMANDS.filter((command) => command.id !== "go-activity" || canSeeActivity),
    [canSeeActivity],
  );

  const runnersRef = useRef(runners);
  const enabledRef = useRef(shortcutsEnabled);
  const availableRef = useRef(available);
  const paletteOpenRef = useRef(paletteOpen);
  const pendingRef = useRef(false);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    runnersRef.current = runners;
    enabledRef.current = shortcutsEnabled;
    availableRef.current = available;
    paletteOpenRef.current = paletteOpen;
  });

  const execute = useCallback((id: CommandId) => {
    if (id === "shortcuts") {
      setHelpOpen(true);
    } else if (id === "go-activity") {
      requestActivityLog();
    } else {
      runnersRef.current[id]?.();
    }
  }, []);

  useEffect(() => {
    const clearPending = () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }

      if (pendingRef.current) {
        pendingRef.current = false;
        setPending(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.isComposing || event.repeat) {
        return;
      }

      const withModifier = event.ctrlKey || event.metaKey;

      if (
        withModifier
        && !event.altKey
        && !event.shiftKey
        && event.key.toLowerCase() === "k"
      ) {
        event.preventDefault();
        clearPending();

        if (paletteOpenRef.current) {
          setPaletteOpen(false);
        } else if (!modalIsOpen()) {
          setPaletteOpen(true);
        }

        return;
      }

      if (!enabledRef.current || withModifier || event.altKey) {
        return;
      }

      if (isTypingTarget(event.target) || modalIsOpen()) {
        clearPending();
        return;
      }

      const key = normalizeKey(event.key, event.shiftKey);

      if (key === null) {
        if (event.key === "Escape") {
          clearPending();
        }

        return;
      }

      const match = resolveShortcut(pendingRef.current, key, availableRef.current);

      clearPending();

      if (match.kind === "pending") {
        pendingRef.current = true;
        setPending(true);

        timerRef.current = window.setTimeout(clearPending, SEQUENCE_WINDOW_MS);
      } else if (match.kind === "run") {
        event.preventDefault();
        execute(match.id);
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);

      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
      }
    };
  }, [execute]);

  const sequenceKeys = useMemo(
    () =>
      available
        .filter((command) => command.keys?.length === 2)
        .map((command) => (command.keys?.[1] ?? "").toUpperCase())
        .join(", "),
    [available],
  );

  return (
    <>
      <button
        type="button"
        className="sidebar-commands"
        aria-haspopup="dialog"
        onClick={() => setPaletteOpen(true)}
      >
        <Search size={17} aria-hidden="true" />

        <span>Commands</span>

        <span className="key-hint" aria-hidden="true">
          <kbd className="kbd">{modifier}</kbd>
          <kbd className="kbd">K</kbd>
        </span>
      </button>

      {pending && (
        <div className="shortcut-pending" role="status">
          <kbd className="kbd">G</kbd>
          Go to… press {sequenceKeys}
        </div>
      )}

      {paletteOpen && (
        <Palette
          commands={available}
          modifier={modifier}
          onRun={(id) => {
            setPaletteOpen(false);
            execute(id);
          }}
          onClose={() => setPaletteOpen(false)}
        />
      )}

      {helpOpen && (
        <ShortcutsHelp
          commands={available}
          modifier={modifier}
          enabled={shortcutsEnabled}
          onClose={() => setHelpOpen(false)}
        />
      )}
    </>
  );
}
