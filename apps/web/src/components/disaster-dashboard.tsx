"use client";

import {
  Activity,
  AlertTriangle,
  Bell,
  Bookmark,
  CircleDot,
  Clock3,
  Database,
  Eye,
  EyeOff,
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
  VolcanoList,
} from "@/components/volcano-list";

import {
  fetchEarthquakes,
  fetchVolcanoes,
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

import type {
  VolcanoEvent,
  VolcanoFeedResponse,
} from "@/types/volcano";

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

type FeedMode =
  | "earthquakes"
  | "volcanoes";

export function DisasterDashboard() {
  const [
    earthquakeData,
    setEarthquakeData,
  ] =
    useState<
      EarthquakeFeedResponse | null
    >(null);

  const [
    volcanoData,
    setVolcanoData,
  ] =
    useState<
      VolcanoFeedResponse | null
    >(null);

  const [
    selectedEarthquake,
    setSelectedEarthquake,
  ] =
    useState<
      EarthquakeEvent | null
    >(null);

  const [
    selectedVolcano,
    setSelectedVolcano,
  ] =
    useState<
      VolcanoEvent | null
    >(null);

  const [
    feedMode,
    setFeedMode,
  ] =
    useState<FeedMode>(
      "earthquakes",
    );

  const [
    earthquakesVisible,
    setEarthquakesVisible,
  ] =
    useState(true);

  const [
    volcanoesVisible,
    setVolcanoesVisible,
  ] =
    useState(true);

  const [
    period,
    setPeriod,
  ] =
    useState<EarthquakePeriod>(
      "day",
    );

  const [
    minMagnitude,
    setMinMagnitude,
  ] =
    useState(2.5);

  const [
    maxDepth,
    setMaxDepth,
  ] =
    useState(700);

  const [
    sort,
    setSort,
  ] =
    useState<EarthquakeSort>(
      "time",
    );

  const [
    searchInput,
    setSearchInput,
  ] =
    useState("");

  const [
    appliedSearch,
    setAppliedSearch,
  ] =
    useState("");

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    refreshing,
    setRefreshing,
  ] =
    useState(false);

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  const [
    refreshCounter,
    setRefreshCounter,
  ] =
    useState(0);

  useEffect(() => {
    const controller =
      new AbortController();

    void Promise.all([
      fetchEarthquakes(
        {
          period,
          minMagnitude,
          maxDepth,
          search: appliedSearch,
          sort,
          limit: 300,
        },
        controller.signal,
      ),

      fetchVolcanoes(
        {
          status: "open",
          days: 3650,
          search: appliedSearch,
          limit: 120,
        },
        controller.signal,
      ),
    ])
      .then(
        ([
          nextEarthquakes,
          nextVolcanoes,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setError(null);

          setEarthquakeData(
            nextEarthquakes,
          );

          setVolcanoData(
            nextVolcanoes,
          );

          setSelectedEarthquake(
            (current) => {
              if (
                current !== null
                && nextEarthquakes
                  .events
                  .some(
                    (event) =>
                      event.id
                      === current.id,
                  )
              ) {
                return nextEarthquakes
                  .events
                  .find(
                    (event) =>
                      event.id
                      === current.id,
                  )
                  ?? null;
              }

              return nextEarthquakes
                .events[0]
                ?? null;
            },
          );

          setSelectedVolcano(
            (current) => {
              if (
                current !== null
                && nextVolcanoes
                  .events
                  .some(
                    (event) =>
                      event.id
                      === current.id,
                  )
              ) {
                return nextVolcanoes
                  .events
                  .find(
                    (event) =>
                      event.id
                      === current.id,
                  )
                  ?? null;
              }

              return null;
            },
          );
        },
      )
      .catch(
        (caughtError: unknown) => {
          if (
            controller.signal.aborted
            || (
              caughtError
                instanceof DOMException
              && caughtError.name
                === "AbortError"
            )
          ) {
            return;
          }

          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to load TerraScope data.",
          );
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
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
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setAppliedSearch(
      searchInput.trim(),
    );
  };

  const handleSelectEarthquake = (
    event: EarthquakeEvent,
  ) => {
    setSelectedVolcano(null);
    setSelectedEarthquake(event);
  };

  const handleSelectVolcano = (
    event: VolcanoEvent,
  ) => {
    setSelectedEarthquake(null);
    setSelectedVolcano(event);
  };

  const openEarthquakeFeed = () => {
    setFeedMode(
      "earthquakes",
    );

    const event =
      earthquakeData?.events[0];

    if (event !== undefined) {
      handleSelectEarthquake(
        event,
      );
    }
  };

  const openVolcanoFeed = () => {
    setFeedMode(
      "volcanoes",
    );

    const event =
      volcanoData?.events[0];

    if (event !== undefined) {
      handleSelectVolcano(
        event,
      );
    }
  };

  const liveSourceCount =
    (
      earthquakeData?.live
        ? 1
        : 0
    )
    +
    (
      volcanoData?.live
        ? 1
        : 0
    );

  const fallbackSources =
    [
      earthquakeData !== null
      && !earthquakeData.live
        ? "USGS earthquakes"
        : null,

      volcanoData !== null
      && !volcanoData.live
        ? "NASA EONET volcanoes"
        : null,
    ]
      .filter(
        (
          value,
        ): value is string =>
          value !== null,
      );

  const eventTimeline =
    useMemo(
      () =>
        (
          earthquakeData?.events
          ?? []
        )
          .slice(0, 18)
          .reverse(),
      [
        earthquakeData?.events,
      ],
    );

  const maximumTimelineMagnitude =
    useMemo(
      () =>
        Math.max(
          1,
          ...eventTimeline.map(
            (event) =>
              event.magnitude,
          ),
        ),
      [
        eventTimeline,
      ],
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
            <strong>
              TerraScope
            </strong>

            <span>
              EARTH MONITOR
            </span>
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

                <span>
                  {label}
                </span>

                {!active && (
                  <small>
                    Soon
                  </small>
                )}
              </button>
            ),
          )}
        </nav>

        <div className="sidebar__bottom">
          <div className="system-mini-card">
            <div className="system-mini-card__header">
              <Database size={15} />

              <span>
                Data network
              </span>
            </div>

            <div className="system-mini-card__row">
              <span className="status-dot status-dot--online" />
              USGS earthquakes
            </div>

            <div className="system-mini-card__row">
              <span className="status-dot status-dot--online" />
              NASA EONET
            </div>
          </div>

          <button
            className="sidebar-settings"
            type="button"
            disabled
          >
            <Settings size={17} />

            <span>
              Settings
            </span>
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
                Multi-Hazard Monitor
              </h1>
            </div>

            <div
              className={
                liveSourceCount === 2
                  ? "source-status source-status--live"
                  : "source-status source-status--fallback"
              }
            >
              <Radio size={14} />

              <span>
                {earthquakeData === null
                  || volcanoData === null
                  ? "CONNECTING"
                  : `${liveSourceCount}/2 LIVE SOURCES`}
              </span>
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

        {fallbackSources.length > 0 && (
          <div className="fallback-banner">
            <AlertTriangle size={17} />

            <div>
              <strong>
                Demonstration fallback active
              </strong>

              <span>
                {fallbackSources.join(
                  ", ",
                )}{" "}
                are currently using local
                fallback data.
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

              <span>
                {error}
              </span>
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
            label="Earthquakes"
            value={
              loading
                ? "—"
                : String(
                    earthquakeData
                      ?.summary
                      .eventCount
                    ?? 0,
                  )
            }
            detail="Filtered seismic events"
          />

          <StatCard
            icon={Mountain}
            label="Volcanic events"
            value={
              loading
                ? "—"
                : String(
                    volcanoData
                      ?.summary
                      .eventCount
                    ?? 0,
                  )
            }
            detail="Open NASA EONET events"
            tone={
              (
                volcanoData
                  ?.summary
                  .eventCount
                ?? 0
              ) > 0
                ? "warning"
                : "default"
            }
          />

          <StatCard
            icon={Gauge}
            label="Strongest quake"
            value={
              loading
                ? "—"
                : `M${
                    earthquakeData
                      ?.summary
                      .strongestMagnitude
                      .toFixed(1)
                    ?? "0.0"
                  }`
            }
            detail="Highest filtered magnitude"
            tone={
              (
                earthquakeData
                  ?.summary
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
                    earthquakeData
                      ?.summary
                      .averageDepthKm
                      .toFixed(1)
                    ?? "0.0"
                  } km`
            }
            detail="Earthquake depth"
          />

          <StatCard
            icon={Waves}
            label="Tsunami flags"
            value={
              loading
                ? "—"
                : String(
                    earthquakeData
                      ?.summary
                      .tsunamiPotentialCount
                    ?? 0,
                  )
            }
            detail="USGS source-level flags"
            tone={
              (
                earthquakeData
                  ?.summary
                  .tsunamiPotentialCount
                ?? 0
              ) > 0
                ? "warning"
                : "default"
            }
          />

          <StatCard
            icon={Radio}
            label="Live sources"
            value={
              loading
                ? "—"
                : `${liveSourceCount}/2`
            }
            detail="USGS + NASA EONET"
          />
        </section>

        <section className="control-panel">
          <div className="control-panel__heading">
            <SlidersHorizontal size={16} />
            <span>
              Observation filters
            </span>
          </div>

          <div className="period-control">
            {periodOptions.map(
              (option) => (
                <button
                  key={option.value}
                  className={
                    period
                      === option.value
                      ? "period-button period-button--active"
                      : "period-button"
                  }
                  type="button"
                  onClick={() =>
                    setPeriod(
                      option.value,
                    )
                  }
                >
                  {option.label}
                </button>
              ),
            )}
          </div>

          <label className="compact-field">
            <span>
              Minimum magnitude
            </span>

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
            <span>
              Maximum depth
            </span>

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
            <span>
              Sort earthquakes
            </span>

            <select
              value={sort}
              onChange={(event) =>
                setSort(
                  event.target.value as EarthquakeSort,
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
              placeholder="Search events..."
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

        <section className="hazard-layer-strip">
          <div className="hazard-layer-strip__heading">
            <Layers3 size={15} />

            <span>
              Map layers
            </span>
          </div>

          <button
            type="button"
            className={
              earthquakesVisible
                ? "hazard-layer-toggle hazard-layer-toggle--active hazard-layer-toggle--earthquake"
                : "hazard-layer-toggle"
            }
            onClick={() =>
              setEarthquakesVisible(
                (value) => !value,
              )
            }
          >
            {earthquakesVisible
              ? <Eye size={14} />
              : <EyeOff size={14} />
            }

            Earthquakes

            <strong>
              {earthquakeData
                ?.returnedEventCount
                ?? 0}
            </strong>
          </button>

          <button
            type="button"
            className={
              volcanoesVisible
                ? "hazard-layer-toggle hazard-layer-toggle--active hazard-layer-toggle--volcano"
                : "hazard-layer-toggle"
            }
            onClick={() =>
              setVolcanoesVisible(
                (value) => !value,
              )
            }
          >
            {volcanoesVisible
              ? <Eye size={14} />
              : <EyeOff size={14} />
            }

            Volcanoes

            <strong>
              {volcanoData
                ?.returnedEventCount
                ?? 0}
            </strong>
          </button>

          <div className="hazard-source-tags">
            <span>
              USGS
            </span>

            <span>
              NASA EONET
            </span>
          </div>
        </section>

        <section className="monitor-grid">
          <article className="panel map-panel">
            <div className="panel-heading">
              <div>
                <span className="panel-kicker">
                  MULTI-HAZARD ACTIVITY
                </span>

                <h2>
                  Global event map
                </h2>
              </div>

              <div className="panel-heading__meta">
                <Layers3 size={14} />
                2 active layers
              </div>
            </div>

            <div className="map-content">
              <EarthquakeMap
                events={
                  earthquakeData
                    ?.events
                  ?? []
                }
                volcanoEvents={
                  volcanoData
                    ?.events
                  ?? []
                }
                earthquakesVisible={
                  earthquakesVisible
                }
                volcanoesVisible={
                  volcanoesVisible
                }
                selectedEarthquake={
                  selectedEarthquake
                }
                selectedVolcano={
                  selectedVolcano
                }
                onSelectEarthquake={
                  handleSelectEarthquake
                }
                onSelectVolcano={
                  handleSelectVolcano
                }
              />

              {selectedEarthquake
                !== null && (
                <div className="event-inspector">
                  <div className="event-inspector__header">
                    <div
                      className={
                        `inspector-magnitude inspector-magnitude--${severityClass(
                          selectedEarthquake
                            .magnitude,
                        )}`
                      }
                    >
                      <span>
                        MAG
                      </span>

                      <strong>
                        {selectedEarthquake
                          .magnitude
                          .toFixed(1)}
                      </strong>
                    </div>

                    <div>
                      <span className="event-inspector__severity">
                        {severityLabel(
                          selectedEarthquake
                            .magnitude,
                        )}{" "}
                        earthquake
                      </span>

                      <h3>
                        {selectedEarthquake
                          .place}
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
                          selectedEarthquake
                            .depthKm,
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
                          selectedEarthquake
                            .latitude,
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
                          selectedEarthquake
                            .longitude,
                          "E",
                          "W",
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Significance
                      </span>

                      <strong>
                        {selectedEarthquake
                          .significance}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <Clock3 size={13} />
                        Occurred
                      </span>

                      <strong>
                        {formatDateTime(
                          selectedEarthquake
                            .occurredAt,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        <Waves size={13} />
                        Tsunami
                      </span>

                      <strong>
                        {selectedEarthquake
                          .tsunamiPotential
                          ? "Potential"
                          : "No flag"}
                      </strong>
                    </div>
                  </div>

                  <div className="event-inspector__footer">
                    <span>
                      USGS event
                    </span>

                    <code>
                      {selectedEarthquake.id}
                    </code>

                    {selectedEarthquake
                      .detailUrl && (
                      <a
                        href={
                          selectedEarthquake
                            .detailUrl
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

              {selectedVolcano
                !== null && (
                <div className="event-inspector event-inspector--volcano">
                  <div className="event-inspector__header">
                    <div className="inspector-volcano">
                      <Mountain
                        size={23}
                        strokeWidth={1.6}
                      />
                    </div>

                    <div>
                      <span className="event-inspector__severity">
                        NASA EONET VOLCANIC EVENT
                      </span>

                      <h3>
                        {selectedVolcano.title}
                      </h3>
                    </div>
                  </div>

                  <div className="event-inspector__grid">
                    <div>
                      <span>
                        <Clock3 size={13} />
                        Observed
                      </span>

                      <strong>
                        {formatDateTime(
                          selectedVolcano
                            .observedAt,
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
                          selectedVolcano
                            .latitude,
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
                          selectedVolcano
                            .longitude,
                          "E",
                          "W",
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Observations
                      </span>

                      <strong>
                        {selectedVolcano
                          .observationCount}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Source
                      </span>

                      <strong>
                        {selectedVolcano
                          .primarySource}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Status
                      </span>

                      <strong>
                        {selectedVolcano.status}
                      </strong>
                    </div>
                  </div>

                  <div className="event-inspector__footer">
                    <span>
                      EONET event
                    </span>

                    <code>
                      {selectedVolcano.id}
                    </code>

                    {(selectedVolcano
                      .sourceUrl
                      || selectedVolcano
                        .detailUrl) && (
                      <a
                        href={
                          selectedVolcano
                            .sourceUrl
                          || selectedVolcano
                            .detailUrl
                        }
                        target="_blank"
                        rel="noreferrer"
                      >
                        Source details
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

                <h2>
                  Hazard activity
                </h2>
              </div>

              <div className="event-count-badge">
                {feedMode
                  === "earthquakes"
                  ? (
                      earthquakeData
                        ?.returnedEventCount
                      ?? 0
                    )
                  : (
                      volcanoData
                        ?.returnedEventCount
                      ?? 0
                    )
                }
              </div>
            </div>

            <div className="feed-tabs">
              <button
                type="button"
                className={
                  feedMode
                    === "earthquakes"
                    ? "feed-tab feed-tab--active"
                    : "feed-tab"
                }
                onClick={
                  openEarthquakeFeed
                }
              >
                Earthquakes
              </button>

              <button
                type="button"
                className={
                  feedMode
                    === "volcanoes"
                    ? "feed-tab feed-tab--active feed-tab--volcano"
                    : "feed-tab"
                }
                onClick={
                  openVolcanoFeed
                }
              >
                Volcanoes
              </button>
            </div>

            <div className="feed-summary">
              {feedMode
                === "earthquakes"
                ? (
                  <>
                    <span>
                      Showing{" "}
                      <strong>
                        {earthquakeData
                          ?.returnedEventCount
                          ?? 0}
                      </strong>
                    </span>

                    <span>
                      Matched{" "}
                      <strong>
                        {earthquakeData
                          ?.matchedEventCount
                          ?? 0}
                      </strong>
                    </span>

                    <span>
                      Source{" "}
                      <strong>
                        {earthquakeData
                          ?.sourceEventCount
                          ?? 0}
                      </strong>
                    </span>
                  </>
                )
                : (
                  <>
                    <span>
                      Open{" "}
                      <strong>
                        {volcanoData
                          ?.returnedEventCount
                          ?? 0}
                      </strong>
                    </span>

                    <span>
                      Obs.{" "}
                      <strong>
                        {volcanoData
                          ?.summary
                          .totalObservationCount
                          ?? 0}
                      </strong>
                    </span>

                    <span>
                      Sources{" "}
                      <strong>
                        {volcanoData
                          ?.summary
                          .sourceReferenceCount
                          ?? 0}
                      </strong>
                    </span>
                  </>
                )
              }
            </div>

            {loading
              && earthquakeData === null
              && volcanoData === null
              ? (
                <div className="event-skeleton-list">
                  {Array.from({
                    length: 8,
                  }).map(
                    (_, index) => (
                      <div
                        className="event-skeleton"
                        key={index}
                      >
                        <div />
                        <span />
                      </div>
                    ),
                  )}
                </div>
              )
              : feedMode
                === "earthquakes"
                ? (
                  <EventList
                    events={
                      earthquakeData
                        ?.events
                      ?? []
                    }
                    selectedEvent={
                      selectedEarthquake
                    }
                    onSelect={
                      handleSelectEarthquake
                    }
                  />
                )
                : (
                  <VolcanoList
                    events={
                      volcanoData
                        ?.events
                      ?? []
                    }
                    selectedEvent={
                      selectedVolcano
                    }
                    onSelect={
                      handleSelectVolcano
                    }
                  />
                )
            }
          </article>
        </section>

        <section className="panel timeline-panel">
          <div className="panel-heading">
            <div>
              <span className="panel-kicker">
                SEISMIC SIGNAL
              </span>

              <h2>
                Earthquake magnitude timeline
              </h2>
            </div>

            <div className="panel-heading__meta">
              <CircleDot size={14} />

              Latest{" "}
              {eventTimeline.length} events
            </div>
          </div>

          <div className="timeline-chart">
            {eventTimeline.length
              === 0
              ? (
                <div className="timeline-empty">
                  No timeline data available.
                </div>
              )
              : eventTimeline.map(
                (event) => {
                  const height =
                    Math.max(
                      8,
                      (
                        event.magnitude
                        / maximumTimelineMagnitude
                      )
                        * 100,
                    );

                  return (
                    <button
                      key={event.id}
                      className="timeline-bar-wrap"
                      type="button"
                      title={`${event.place} — M${event.magnitude.toFixed(1)}`}
                      onClick={() =>
                        handleSelectEarthquake(
                          event,
                        )
                      }
                    >
                      <span className="timeline-value">
                        {event.magnitude
                          .toFixed(1)}
                      </span>

                      <span
                        className={
                          `timeline-bar timeline-bar--${severityClass(
                            event.magnitude,
                          )}`
                        }
                        style={{
                          height:
                            `${height}%`,
                        }}
                      />

                      <span className="timeline-time">
                        {new Date(
                          event.occurredAt,
                        ).toLocaleTimeString(
                          [],
                          {
                            hour:
                              "2-digit",
                            minute:
                              "2-digit",
                          },
                        )}
                      </span>
                    </button>
                  );
                },
              )
            }
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
            Earthquakes: USGS
          </div>

          <div>
            Volcanoes: NASA EONET
          </div>

          <div>
            Map: OpenStreetMap contributors
          </div>
        </footer>
      </main>
    </div>
  );
}
