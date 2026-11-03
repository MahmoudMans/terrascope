"use client";

import type { LucideIcon } from "lucide-react";

import {
  useCallback,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

export type WorkspaceTab = {
  id: string;
  label: string;
  icon?: LucideIcon;
  description?: string;
  badge?: string | number;
  render: () => ReactNode;
};

type WorkspaceTabsProps = {
  idPrefix: string;
  label: string;
  tabs: WorkspaceTab[];
  activeId: string;
  onChange: (id: string) => void;
};

export function WorkspaceTabs({
  idPrefix,
  label,
  tabs,
  activeId,
  onChange,
}: WorkspaceTabsProps) {
  const [visited, setVisited] = useState<ReadonlySet<string>>(
    () => new Set([activeId]),
  );

  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  const activeTab =
    tabs.find((tab) => tab.id === activeId) ?? tabs[0];

  if (!visited.has(activeTab.id)) {
    setVisited(new Set([...visited, activeTab.id]));
  }

  const select = useCallback(
    (id: string, moveFocus: boolean) => {
      setVisited((current) =>
        current.has(id) ? current : new Set([...current, id]),
      );

      onChange(id);

      if (moveFocus) {
        tabRefs.current[id]?.focus();
      }
    },
    [onChange],
  );

  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    index: number,
  ) => {
    let nextIndex: number | null = null;

    switch (event.key) {
      case "ArrowRight":
        nextIndex = (index + 1) % tabs.length;
        break;
      case "ArrowLeft":
        nextIndex = (index - 1 + tabs.length) % tabs.length;
        break;
      case "Home":
        nextIndex = 0;
        break;
      case "End":
        nextIndex = tabs.length - 1;
        break;
      default:
        return;
    }

    event.preventDefault();
    select(tabs[nextIndex].id, true);
  };

  return (
    <div className="workspace-tabs">
      <div className="workspace-tabs__bar">
        <div
          className="workspace-tabs__list"
          role="tablist"
          aria-label={label}
        >
          {tabs.map((tab, index) => {
            const selected = tab.id === activeTab.id;
            const Icon = tab.icon;

            return (
              <button
                key={tab.id}
                ref={(element: HTMLButtonElement | null) => {
                  tabRefs.current[tab.id] = element;
                }}
                id={`${idPrefix}-tab-${tab.id}`}
                type="button"
                role="tab"
                aria-selected={selected}
                aria-controls={`${idPrefix}-panel-${tab.id}`}
                tabIndex={selected ? 0 : -1}
                className={
                  selected
                    ? "workspace-tab workspace-tab--active"
                    : "workspace-tab"
                }
                onClick={() => select(tab.id, false)}
                onKeyDown={(event) => handleKeyDown(event, index)}
              >
                {Icon !== undefined && <Icon size={15} aria-hidden="true" />}

                <span>{tab.label}</span>

                {tab.badge !== undefined && (
                  <small className="workspace-tab__badge">
                    {tab.badge}
                  </small>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {activeTab.description !== undefined && (
        <p className="workspace-tabs__description">
          {activeTab.description}
        </p>
      )}

      {tabs.map((tab) =>
        visited.has(tab.id) ? (
          <div
            key={tab.id}
            id={`${idPrefix}-panel-${tab.id}`}
            role="tabpanel"
            aria-labelledby={`${idPrefix}-tab-${tab.id}`}
            className="workspace-tabs__panel"
            hidden={tab.id !== activeTab.id}
          >
            {tab.render()}
          </div>
        ) : null,
      )}
    </div>
  );
}
