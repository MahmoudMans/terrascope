"use client";

import {
  Activity,
  AlertTriangle,
  BarChart3,
  Bell,
  Bookmark,
  CircleDot,
  Clock3,
  Database,
  Gauge,
  Globe2,
  Layers3,
  Map,
  MapPin,
  Mountain,
  Radio,
  RefreshCw,
  Search,
  Settings,
  ShieldAlert,
  SlidersHorizontal,
  Sparkles,
  Waves,
  Zap,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  EarthquakeMap,
} from "@/components/earthquake-map";

import {
  EventList,
} from "@/components/event-list";

import {
  StatCard,
} from "@/components/stat-card";

import {
  fetchEarthquakes,
} from "@/lib/api";

import {
  formatCoordinate,
  formatDateTime,
  formatDepth,
  severityClass,
  severityLabel,
} from "@/lib/format";

import type {
  EarthquakeEvent,
  EarthquakeFeedResponse,
  EarthquakePeriod,
  EarthquakeSort,
} from "@/types/earthquake";

const periodOptions: Array<{
  value: EarthquakePeriod;
  label: string;
}> = [
  {
    value: "hour",
    label: "Past hour",
  },
  {
    value: "day",
    label: "Past 24h",
  },
  {
    value: "week",
    label: "Past 7d",
  },
  {
    value: "month",
    label: "Past 30d",
  },
];

const navItems = [
  {
    label: "Monitor",
    icon: Map,
    active: true,
  },
  {
    label: "Timeline",
    icon: Clock3,
    active: false,
  },
  {
    label: "Regions",
    icon: Globe2,
    active: false,
  },
  {
    label: "Simulations",
    icon: Sparkles,
    active: false,
  },
  {
    label: "Saved",
    icon: Bookmark,
    active: false,
  },
];

export function DisasterDashboard() {
  const [
    data,
    setData,
  ] = useState<EarthquakeFeedResponse | null>(
    null,
  );

  const [
    selectedEvent,
    setSelectedEvent,
  ] = useState<EarthquakeEvent | null>(
    null,
  );

  const [
    period,
    setPeriod,
  ] = useState<EarthquakePeriod>("day");

  const [
    minMagnitude,
    setMinMagnitude,
  ] = useState(2.5);

  const [
    maxDepth,
    setMaxDepth,
  ] = useState(700);

  const [
    sort,
    setSort,
  ] = useState<EarthquakeSort>("time");

  const [
    searchInput,
    setSearchInput,
  ] = useState("");

  const [
    appliedSearch,
    setAppliedSearch,
  ] = useState("");

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    refreshing,
    setRefreshing,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState<string | null>(null);

  const [
    refreshCounter,
    setRefreshCounter,
  ] = useState(0);

  useEffect(() => {
    const controller =
      new AbortController();

    void fetchEarthquakes(
      {
        period,
        minMagnitude,
        maxDepth,
        search: appliedSearch,
        sort,
        limit: 300,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) {
          return;
        }

        setError(null);

        setData(response);

        setSelectedEvent(
          (current) => {
            if (
              current !== null
              && response.events.some(
                (event) =>
                  event.id === current.id,
              )
            ) {
              return response.events.find(
                (event) =>
                  event.id === current.id,
              ) ?? null;
            }

            return response.events[0]
              ?? null;
          },
        );
      })
      .catch((caughtError: unknown) => {
        if (
          controller.signal.aborted
          || (
            caughtError instanceof DOMException
            && caughtError.name === "AbortError"
          )
        ) {
          return;
        }

        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "Unable to load earthquake data.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
          setRefreshing(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [
    period,
    minMagnitude,
    maxDepth,
    appliedSearch,
    sort,
    refreshCounter,
  ]);

  const handleRefresh = () => {
    setRefreshing(true);

    setRefreshCounter(
      (value) => value + 1,
    );
  };

  const handleSearch = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setAppliedSearch(
      searchInput.trim(),
    );
  };

  const sourceLabel =
    data?.live
      ? "LIVE USGS"
      : "DEMO FALLBACK";

  const eventTimeline =
    useMemo(
      () =>
        (data?.events ?? [])
          .slice(0, 18)
          .reverse(),
      [data?.events],
    );

  const maximumTimelineMagnitude =
    useMemo(
      () =>
        Math.max(
          1,
          ...eventTimeline.map(
            (event) => event.magnitude,
          ),
        ),
      [eventTimeline],
    );

  return (
    <div className="application-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand__mark">
            <Globe2 size={24} />
            <span className="brand__pulse" />
          </div>

          <div className="brand__text">
            <strong>TerraScope</strong>
            <span>EARTH MONITOR</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <span className="sidebar-nav__section">
            Observatory
          </span>

          {navItems.map(
            ({
              label,
              icon: Icon,
              active,
            }) => (
              <button
                className={
                  active
                    ? "sidebar-link sidebar-link--active"
                    : "sidebar-link"
                }
                key={label}
                type="button"
                disabled={!active}
              >
                <Icon size={18} />
                <span>{label}</span>

                {!active && (
                  <small>Soon</small>
                )}
              </button>
            ),
          )}
        </nav>

        <div className="sidebar__bottom">
          <div className="system-mini-card">
            <div className="system-mini-card__header">
              <Database size={15} />
              <span>Data network</span>
            </div>

            <div className="system-mini-card__row">
              <span className="status-dot status-dot--online" />
              USGS seismic feed
            </div>

            <div className="system-mini-card__row system-mini-card__row--muted">
              <span className="status-dot" />
              More sources soon
            </div>
          </div>

          <button
            className="sidebar-settings"
            type="button"
            disabled
          >
            <Settings size={17} />
            <span>Settings</span>
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="topbar__heading">
            <div>
              <span className="eyebrow">
                GLOBAL OBSERVATORY
              </span>

              <h1>
                Earthquake Monitor
              </h1>
            </div>

            <div
              className={
                data?.live
                  ? "source-status source-status--live"
                  : "source-status source-status--fallback"
              }
            >
              <Radio size={14} />
              <span>{sourceLabel}</span>
            </div>
          </div>

          <div className="topbar__actions">
            <button
              className="icon-button"
              type="button"
              title="Notifications"
            >
              <Bell size={18} />
              <span className="notification-dot" />
            </button>

            <button
              className="refresh-button"
              type="button"
              onClick={handleRefresh}
              disabled={refreshing}
            >
              <RefreshCw
                size={16}
                className={
                  refreshing
                    ? "spin"
                    : ""
                }
              />

              <span>
                {refreshing
                  ? "Refreshing"
                  : "Refresh"}
              </span>
            </button>
          </div>
        </header>

        {!data?.live && data !== null && (
          <div className="fallback-banner">
            <AlertTriangle size={17} />

            <div>
              <strong>
                USGS feed unavailable
              </strong>

              <span>
                TerraScope is displaying clearly
                labelled local demonstration data.
              </span>
            </div>
          </div>
        )}

        {error !== null && (
          <div className="error-banner">
            <ShieldAlert size={18} />

            <div>
              <strong>
                Monitoring connection error
              </strong>

              <span>{error}</span>
            </div>

            <button
              type="button"
              onClick={handleRefresh}
            >
              Retry
            </button>
          </div>
        )}

        <section className="stats-grid">
          <StatCard
            icon={Activity}
            label="Detected events"
            value={
              loading
                ? "—"
                : String(
                    data?.summary.eventCount
                      ?? 0,
                  )
            }
            detail={`Within ${periodOptions.find(
              (option) =>
                option.value === period,
            )?.label.toLowerCase() ?? period}`}
          />

          <StatCard
            icon={Gauge}
            label="Strongest"
            value={
              loading
                ? "—"
                : `M${
                    data?.summary.strongestMagnitude
                      .toFixed(1)
                    ?? "0.0"
                  }`
            }
            detail="Highest filtered magnitude"
            tone={
              (
                data?.summary
                  .strongestMagnitude
                ?? 0
              ) >= 6
                ? "danger"
                : "default"
            }
          />

          <StatCard
            icon={Mountain}
            label="Average depth"
            value={
              loading
                ? "—"
                : `${
                    data?.summary.averageDepthKm
                      .toFixed(1)
                    ?? "0.0"
                  } km`
            }
            detail="Below surface"
          />

          <StatCard
            icon={Waves}
            label="Tsunami flags"
            value={
              loading
                ? "—"
                : String(
                    data?.summary
                      .tsunamiPotentialCount
                      ?? 0,
                  )
            }
            detail="Source-level potential flag"
            tone={
              (
                data?.summary
                  .tsunamiPotentialCount
                ?? 0
              ) > 0
                ? "warning"
                : "default"
            }
          />

          <StatCard
            icon={Zap}
            label="Significant"
            value={
              loading
                ? "—"
                : String(
                    data?.summary
                      .significantEventCount
                      ?? 0,
                  )
            }
            detail="High significance or M5+"
          />
        </section>

        <section className="control-panel">
          <div className="control-panel__heading">
            <SlidersHorizontal size={16} />
            <span>Observation filters</span>
          </div>

          <div className="period-control">
            {periodOptions.map(
              (option) => (
                <button
                  key={option.value}
                  className={
                    period === option.value
                      ? "period-button period-button--active"
                      : "period-button"
                  }
                  type="button"
                  onClick={() =>
                    setPeriod(option.value)
                  }
                >
                  {option.label}
                </button>
              ),
            )}
          </div>

          <label className="compact-field">
            <span>Minimum magnitude</span>

            <select
              value={minMagnitude}
              onChange={(event) =>
                setMinMagnitude(
                  Number(
                    event.target.value,
                  ),
                )
              }
            >
              <option value={0}>
                All magnitudes
              </option>

              <option value={1}>
                M1.0+
              </option>

              <option value={2.5}>
                M2.5+
              </option>

              <option value={4}>
                M4.0+
              </option>

              <option value={4.5}>
                M4.5+
              </option>

              <option value={5}>
                M5.0+
              </option>

              <option value={6}>
                M6.0+
              </option>
            </select>
          </label>

          <label className="compact-field">
            <span>Maximum depth</span>

            <select
              value={maxDepth}
              onChange={(event) =>
                setMaxDepth(
                  Number(
                    event.target.value,
                  ),
                )
              }
            >
              <option value={50}>
                50 km
              </option>

              <option value={100}>
                100 km
              </option>

              <option value={300}>
                300 km
              </option>

              <option value={700}>
                700 km
              </option>

              <option value={1000}>
                1000 km
              </option>
            </select>
          </label>

          <label className="compact-field">
            <span>Sort by</span>

            <select
              value={sort}
              onChange={(event) =>
                setSort(
                  event.target
                    .value as EarthquakeSort,
                )
              }
            >
              <option value="time">
                Latest first
              </option>

              <option value="magnitude">
                Magnitude
              </option>

              <option value="depth">
                Depth
              </option>
            </select>
          </label>

          <form
            className="search-field"
            onSubmit={handleSearch}
          >
            <Search size={15} />

            <input
              type="search"
              placeholder="Search location..."
              value={searchInput}
              onChange={(event) =>
                setSearchInput(
                  event.target.value,
                )
              }
            />

            <button type="submit">
              Search
            </button>
          </form>
        </section>

        <section className="monitor-grid">
          <article className="panel map-panel">
            <div className="panel-heading">
              <div>
                <span className="panel-kicker">
                  SEISMIC ACTIVITY
                </span>

                <h2>Global event map</h2>
              </div>

              <div className="panel-heading__meta">
                <Layers3 size={14} />
                OpenStreetMap
              </div>
            </div>

            <div className="map-content">
              <EarthquakeMap
                events={data?.events ?? []}
                selectedEvent={
                  selectedEvent
                }
                onSelect={
                  setSelectedEvent
                }
              />

              {selectedEvent !== null && (
                <div className="event-inspector">
                  <div className="event-inspector__header">
                    <div
                      className={`inspector-magnitude inspector-magnitude--${severityClass(
                        selectedEvent.magnitude,
                      )}`}
                    >
                      <span>MAG</span>

                      <strong>
                        {selectedEvent.magnitude.toFixed(
                          1,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span className="event-inspector__severity">
                        {severityLabel(
                          selectedEvent.magnitude,
                        )}{" "}
                        earthquake
                      </span>

                      <h3>
                        {selectedEvent.place}
                      </h3>
                    </div>
                  </div>

                  <div className="event-inspector__grid">
                    <div>
                      <span>
                        <Mountain size={13} />
                        Depth
                      </span>

                      <strong>
                        {formatDepth(
                          selectedEvent.depthKm,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <MapPin size={13} />
                        Latitude
                      </span>

                      <strong>
                        {formatCoordinate(
                          selectedEvent.latitude,
                          "N",
                          "S",
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <MapPin size={13} />
                        Longitude
                      </span>

                      <strong>
                        {formatCoordinate(
                          selectedEvent.longitude,
                          "E",
                          "W",
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <BarChart3 size={13} />
                        Significance
                      </span>

                      <strong>
                        {selectedEvent.significance}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <Clock3 size={13} />
                        Occurred
                      </span>

                      <strong>
                        {formatDateTime(
                          selectedEvent.occurredAt,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <Waves size={13} />
                        Tsunami
                      </span>

                      <strong>
                        {selectedEvent
                          .tsunamiPotential
                          ? "Potential"
                          : "No flag"}
                      </strong>
                    </div>
                  </div>

                  <div className="event-inspector__footer">
                    <span>
                      Event ID
                    </span>

                    <code>
                      {selectedEvent.id}
                    </code>

                    {selectedEvent.detailUrl
                      && (
                        <a
                          href={
                            selectedEvent.detailUrl
                          }
                          target="_blank"
                          rel="noreferrer"
                        >
                          USGS details
                        </a>
                      )}
                  </div>
                </div>
              )}
            </div>
          </article>

          <article className="panel feed-panel">
            <div className="panel-heading">
              <div>
                <span className="panel-kicker">
                  EVENT STREAM
                </span>

                <h2>Recent activity</h2>
              </div>

              <div className="event-count-badge">
                {data?.returnedEventCount
                  ?? 0}
              </div>
            </div>

            <div className="feed-summary">
              <span>
                Showing{" "}
                <strong>
                  {data?.returnedEventCount
                    ?? 0}
                </strong>
              </span>

              <span>
                Matched{" "}
                <strong>
                  {data?.matchedEventCount
                    ?? 0}
                </strong>
              </span>

              <span>
                Source{" "}
                <strong>
                  {data?.sourceEventCount
                    ?? 0}
                </strong>
              </span>
            </div>

            {loading && data === null ? (
              <div className="event-skeleton-list">
                {Array.from({
                  length: 8,
                }).map((_, index) => (
                  <div
                    className="event-skeleton"
                    key={index}
                  >
                    <div />
                    <span />
                  </div>
                ))}
              </div>
            ) : (
              <EventList
                events={
                  data?.events ?? []
                }
                selectedEvent={
                  selectedEvent
                }
                onSelect={
                  setSelectedEvent
                }
              />
            )}
          </article>
        </section>

        <section className="panel timeline-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">
                RECENT SIGNAL
              </span>

              <h2>
                Magnitude timeline
              </h2>
            </div>

            <div className="panel-heading__meta">
              <CircleDot size={14} />
              Latest {eventTimeline.length} events
            </div>
          </div>

          <div className="timeline-chart">
            {eventTimeline.length === 0 ? (
              <div className="timeline-empty">
                No timeline data available.
              </div>
            ) : (
              eventTimeline.map(
                (event) => {
                  const height =
                    Math.max(
                      8,
                      (
                        event.magnitude
                        / maximumTimelineMagnitude
                      ) * 100,
                    );

                  return (
                    <button
                      key={event.id}
                      className="timeline-bar-wrap"
                      type="button"
                      title={`${event.place} — M${event.magnitude.toFixed(
                        1,
                      )}`}
                      onClick={() =>
                        setSelectedEvent(
                          event,
                        )
                      }
                    >
                      <span className="timeline-value">
                        {event.magnitude.toFixed(
                          1,
                        )}
                      </span>

                      <span
                        className={`timeline-bar timeline-bar--${severityClass(
                          event.magnitude,
                        )}`}
                        style={{
                          height: `${height}%`,
                        }}
                      />

                      <span className="timeline-time">
                        {new Date(
                          event.occurredAt,
                        ).toLocaleTimeString(
                          [],
                          {
                            hour: "2-digit",
                            minute:
                              "2-digit",
                          },
                        )}
                      </span>
                    </button>
                  );
                },
              )
            )}
          </div>
        </section>

        <footer className="application-footer">
          <div>
            <Activity size={14} />
            <span>
              TerraScope Observatory
            </span>
          </div>

          <div>
            Earthquake data: USGS Earthquake
            Hazards Program
          </div>

          <div>
            Map data: OpenStreetMap contributors
          </div>
        </footer>
      </main>
    </div>
  );
}
