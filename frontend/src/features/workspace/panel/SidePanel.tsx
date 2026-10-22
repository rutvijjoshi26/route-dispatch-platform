import { useId, useState, type KeyboardEvent, type ReactNode } from 'react';

export interface PanelTab {
  id: string;
  label: string;
  count?: number;
  content: ReactNode;
}

/** Accessible tab strip (arrow keys move between tabs). */
export function SidePanel({ tabs }: { tabs: PanelTab[] }) {
  const baseId = useId();
  const [activeId, setActiveId] = useState(tabs[0]?.id);
  const active = tabs.find((tab) => tab.id === activeId) ?? tabs[0];

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    const index = tabs.findIndex((tab) => tab.id === active?.id);
    const offset = event.key === 'ArrowRight' ? 1 : -1;
    const next = tabs[(index + offset + tabs.length) % tabs.length];
    if (next) {
      setActiveId(next.id);
      document.getElementById(`${baseId}-tab-${next.id}`)?.focus();
    }
  }

  return (
    <aside className="side-panel">
      <div className="tabs" role="tablist" aria-label="Plan details" onKeyDown={onKeyDown}>
        {tabs.map((tab) => {
          const selected = tab.id === active?.id;
          return (
            <button
              key={tab.id}
              id={`${baseId}-tab-${tab.id}`}
              type="button"
              role="tab"
              className="tab"
              aria-selected={selected}
              aria-controls={`${baseId}-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => setActiveId(tab.id)}
            >
              {tab.label}
              {tab.count !== undefined && <span className="tab-count">{tab.count}</span>}
            </button>
          );
        })}
      </div>
      {active && (
        <div
          id={`${baseId}-panel-${active.id}`}
          role="tabpanel"
          aria-labelledby={`${baseId}-tab-${active.id}`}
          className="tab-panel"
          tabIndex={0}
        >
          {active.content}
        </div>
      )}
    </aside>
  );
}
