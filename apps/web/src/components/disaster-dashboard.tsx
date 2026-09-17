"use client";

import {
  Activity,
  AlertTriangle,
  Bell,
  Bookmark,
  BookmarkPlus,
  CircleDot,
  Clock3,
  CloudLightning,
  Database,
  Eye,
  EyeOff,
  Flame,
  Gauge,
  Globe2,
  Layers3,
  Map,
  Mountain,
  Radio,
  RefreshCw,
  Search,
  Settings,
  ShieldAlert,
  SlidersHorizontal,
  Sparkles,
  Radar,
  BellRing,
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
  HazardEventList,
} from "@/components/hazard-event-list";

import {
  StatCard,
} from "@/components/stat-card";

import {
  SimulationPanel,
} from "@/components/simulation-panel";

import {
  RegionalIntelligencePanel,
} from "@/components/regional-intelligence-panel";

import {
  AlertCenterPanel,
} from "@/components/alert-center-panel";

import {
  VolcanoList,
} from "@/components/volcano-list";

import {
  WatchlistPanel,
} from "@/components/watchlist-panel";

import {
  fetchEarthquakes,
  fetchNaturalHazards,
  fetchVolcanoes,
  saveWatchlistEvent,
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
  NaturalHazardEvent,
  NaturalHazardFeedResponse,
} from "@/types/natural-hazard";

import type {
  VolcanoEvent,
  VolcanoFeedResponse,
} from "@/types/volcano";

import type {
  SaveWatchlistEventRequest,
} from "@/types/watchlist";

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
    active: true,
  },
  {
    label: "Regional",
    icon: Radar,
    active: true,
  },
  {
    label: "Alerts",
    icon: BellRing,
    active: true,
  },
  {
    label: "Saved",
    icon: Bookmark,
    active: true,
  },
];

type FeedMode =
  | "earthquakes"
  | "volcanoes"
  | "wildfires"
  | "severe-storms";

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
    wildfireData,
    setWildfireData,
  ] =
    useState<
      NaturalHazardFeedResponse | null
    >(null);

  const [
    stormData,
    setStormData,
  ] =
    useState<
      NaturalHazardFeedResponse | null
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
    selectedNaturalHazard,
    setSelectedNaturalHazard,
  ] =
    useState<
      NaturalHazardEvent | null
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
    wildfiresVisible,
    setWildfiresVisible,
  ] =
    useState(true);

  const [
    stormsVisible,
    setStormsVisible,
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

  const [
    watchlistOpen,
    setWatchlistOpen,
  ] =
    useState(false);

  const [
    simulationOpen,
    setSimulationOpen,
  ] =
    useState(false);

  const [
    regionalOpen,
    setRegionalOpen,
  ] =
    useState(false);

  const [
    alertCenterOpen,
    setAlertCenterOpen,
  ] =
    useState(false);

  const [
    watchlistRefreshCounter,
    setWatchlistRefreshCounter,
  ] =
    useState(0);

  const [
    savingSelected,
    setSavingSelected,
  ] =
    useState(false);

  const [
    watchlistMessage,
    setWatchlistMessage,
  ] =
    useState<string | null>(
      null,
    );

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

      fetchNaturalHazards(
        {
          type: "wildfires",
          status: "open",
          days: 90,
          search: appliedSearch,
          limit: 120,
        },
        controller.signal,
      ),

      fetchNaturalHazards(
        {
          type: "severe-storms",
          status: "open",
          days: 90,
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
          nextWildfires,
          nextStorms,
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

          setWildfireData(
            nextWildfires,
          );

          setStormData(
            nextStorms,
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

          setSelectedNaturalHazard(
            (current) => {
              if (current === null) {
                return null;
              }

              const events =
                current.hazardType
                  === "wildfires"
                  ? nextWildfires.events
                  : nextStorms.events;

              return events.find(
                (event) =>
                  event.id
                  === current.id,
              )
                ?? null;
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

  const buildWatchlistRequest =
    (): SaveWatchlistEventRequest | null => {
      if (selectedEarthquake !== null) {
        return {
          sourceEventId:
            selectedEarthquake.id,
          hazardType:
            "earthquakes",
          title:
            selectedEarthquake.place,
          description:
            `Magnitude ${selectedEarthquake.magnitude.toFixed(1)} earthquake`,
          latitude:
            selectedEarthquake.latitude,
          longitude:
            selectedEarthquake.longitude,
          observedAt:
            selectedEarthquake.occurredAt,
          sourceProvider:
            "USGS",
          sourceUrl:
            selectedEarthquake.detailUrl,
          magnitudeValue:
            selectedEarthquake.magnitude,
          magnitudeUnit:
            "M",
          priority:
            selectedEarthquake.magnitude >= 7
              ? "CRITICAL"
              : selectedEarthquake.magnitude >= 6
                ? "HIGH"
                : "MEDIUM",
          notes: "",
        };
      }

      if (selectedVolcano !== null) {
        return {
          sourceEventId:
            selectedVolcano.id,
          hazardType:
            "volcanoes",
          title:
            selectedVolcano.title,
          description:
            selectedVolcano.description,
          latitude:
            selectedVolcano.latitude,
          longitude:
            selectedVolcano.longitude,
          observedAt:
            selectedVolcano.observedAt,
          sourceProvider:
            selectedVolcano.primarySource,
          sourceUrl:
            selectedVolcano.sourceUrl
            || selectedVolcano.detailUrl,
          magnitudeValue:
            null,
          magnitudeUnit:
            "",
          priority:
            "MEDIUM",
          notes: "",
        };
      }

      if (
        selectedNaturalHazard
        !== null
      ) {
        return {
          sourceEventId:
            selectedNaturalHazard.id,
          hazardType:
            selectedNaturalHazard.hazardType,
          title:
            selectedNaturalHazard.title,
          description:
            selectedNaturalHazard.description,
          latitude:
            selectedNaturalHazard.latitude,
          longitude:
            selectedNaturalHazard.longitude,
          observedAt:
            selectedNaturalHazard.observedAt,
          sourceProvider:
            selectedNaturalHazard.primarySource,
          sourceUrl:
            selectedNaturalHazard.sourceUrl
            || selectedNaturalHazard.detailUrl,
          magnitudeValue:
            selectedNaturalHazard.magnitudeValue,
          magnitudeUnit:
            selectedNaturalHazard.magnitudeUnit,
          priority:
            selectedNaturalHazard.hazardType
              === "severe-storms"
              && (
                selectedNaturalHazard.magnitudeValue
                ?? 0
              ) >= 64
                ? "HIGH"
                : "MEDIUM",
          notes: "",
        };
      }

      return null;
    };

  const handleSaveSelected = () => {
    const request =
      buildWatchlistRequest();

    if (request === null) {
      return;
    }

    setSavingSelected(true);
    setWatchlistMessage(null);

    void saveWatchlistEvent(
      request,
    )
      .then(() => {
        setWatchlistMessage(
          "Event saved to watchlist.",
        );

        setWatchlistRefreshCounter(
          (value) => value + 1,
        );
      })
      .catch(
        (caughtError: unknown) => {
          setWatchlistMessage(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to save event.",
          );
        },
      )
      .finally(() => {
        setSavingSelected(false);
      });
  };

  const handleSelectEarthquake = (
    event: EarthquakeEvent,
  ) => {
    setSelectedVolcano(null);
    setSelectedNaturalHazard(null);
    setSelectedEarthquake(event);
    setFeedMode("earthquakes");
  };

  const handleSelectVolcano = (
    event: VolcanoEvent,
  ) => {
    setSelectedEarthquake(null);
    setSelectedNaturalHazard(null);
    setSelectedVolcano(event);
    setFeedMode("volcanoes");
  };

  const handleSelectNaturalHazard = (
    event: NaturalHazardEvent,
  ) => {
    setSelectedEarthquake(null);
    setSelectedVolcano(null);
    setSelectedNaturalHazard(event);
    setFeedMode(event.hazardType);
  };

  const switchFeed = (
    mode: FeedMode,
  ) => {
    setFeedMode(mode);

    if (mode === "earthquakes") {
      const event =
        earthquakeData?.events[0];

      if (event !== undefined) {
        handleSelectEarthquake(event);
      }

      return;
    }

    if (mode === "volcanoes") {
      const event =
        volcanoData?.events[0];

      if (event !== undefined) {
        handleSelectVolcano(event);
      }

      return;
    }

    const events =
      mode === "wildfires"
        ? wildfireData?.events
        : stormData?.events;

    const event = events?.[0];

    if (event !== undefined) {
      handleSelectNaturalHazard(
        event,
      );
    }
  };

  const liveSourceCount =
    [
      earthquakeData?.live,
      volcanoData?.live,
      wildfireData?.live,
      stormData?.live,
    ]
      .filter(Boolean)
      .length;

  const activeLayerCount =
    [
      earthquakesVisible,
      volcanoesVisible,
      wildfiresVisible,
      stormsVisible,
    ]
      .filter(Boolean)
      .length;

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

      wildfireData !== null
      && !wildfireData.live
        ? "NASA EONET wildfires"
        : null,

      stormData !== null
      && !stormData.live
        ? "NASA EONET severe storms"
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

  const currentFeedCount =
    feedMode === "earthquakes"
      ? earthquakeData?.returnedEventCount ?? 0
      : feedMode === "volcanoes"
        ? volcanoData?.returnedEventCount ?? 0
        : feedMode === "wildfires"
          ? wildfireData?.returnedEventCount ?? 0
          : stormData?.returnedEventCount ?? 0;

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
                  label === "Monitor"
                    ? "sidebar-link sidebar-link--active"
                    : "sidebar-link"
                }
                key={label}
                type="button"
                disabled={!active}
                onClick={() => {
                  if (
                    label
                    === "Simulations"
                  ) {
                    setSimulationOpen(
                      true,
                    );
                  }

                  if (
                    label
                    === "Regional"
                  ) {
                    setRegionalOpen(
                      true,
                    );
                  }

                  if (
                    label
                    === "Alerts"
                  ) {
                    setAlertCenterOpen(
                      true,
                    );
                  }

                  if (label === "Saved") {
                    setWatchlistOpen(true);
                  }
                }}
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
              USGS seismic
            </div>

            <div className="system-mini-card__row">
              <span className="status-dot status-dot--online" />
              NASA EONET hazards
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
                liveSourceCount === 4
                  ? "source-status source-status--live"
                  : "source-status source-status--fallback"
              }
            >
              <Radio size={14} />

              <span>
                {earthquakeData === null
                  || volcanoData === null
                  || wildfireData === null
                  || stormData === null
                  ? "CONNECTING"
                  : `${liveSourceCount}/4 LIVE FEEDS`}
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
              className="save-event-button"
              type="button"
              onClick={handleSaveSelected}
              disabled={
                savingSelected
                || (
                  selectedEarthquake === null
                  && selectedVolcano === null
                  && selectedNaturalHazard === null
                )
              }
            >
              <BookmarkPlus
                size={16}
              />

              <span>
                {savingSelected
                  ? "Saving"
                  : "Save event"}
              </span>
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
                currently use local demo data.
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
            detail="USGS seismic events"
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
            detail="NASA EONET"
          />

          <StatCard
            icon={Flame}
            label="Wildfires"
            value={
              loading
                ? "—"
                : String(
                    wildfireData
                      ?.summary
                      .eventCount
                    ?? 0,
                  )
            }
            detail="Open EONET events"
            tone={
              (
                wildfireData
                  ?.summary
                  .eventCount
                ?? 0
              ) > 0
                ? "warning"
                : "default"
            }
          />

          <StatCard
            icon={CloudLightning}
            label="Severe storms"
            value={
              loading
                ? "—"
                : String(
                    stormData
                      ?.summary
                      .eventCount
                    ?? 0,
                  )
            }
            detail="Open EONET events"
            tone={
              (
                stormData
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
            detail="Current filter"
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
            detail="USGS source flags"
          />

          <StatCard
            icon={Radio}
            label="Live feeds"
            value={
              loading
                ? "—"
                : `${liveSourceCount}/4`
            }
            detail="External data streams"
          />

          <StatCard
            icon={Layers3}
            label="Active layers"
            value={
              String(
                activeLayerCount,
              )
            }
            detail="Visible map overlays"
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
                All
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
              placeholder="Search all hazards..."
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

          <button
            type="button"
            className={
              wildfiresVisible
                ? "hazard-layer-toggle hazard-layer-toggle--active hazard-layer-toggle--wildfire"
                : "hazard-layer-toggle"
            }
            onClick={() =>
              setWildfiresVisible(
                (value) => !value,
              )
            }
          >
            {wildfiresVisible
              ? <Eye size={14} />
              : <EyeOff size={14} />
            }

            Wildfires

            <strong>
              {wildfireData
                ?.returnedEventCount
                ?? 0}
            </strong>
          </button>

          <button
            type="button"
            className={
              stormsVisible
                ? "hazard-layer-toggle hazard-layer-toggle--active hazard-layer-toggle--storm"
                : "hazard-layer-toggle"
            }
            onClick={() =>
              setStormsVisible(
                (value) => !value,
              )
            }
          >
            {stormsVisible
              ? <Eye size={14} />
              : <EyeOff size={14} />
            }

            Storms

            <strong>
              {stormData
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
                  GLOBAL HAZARD ACTIVITY
                </span>

                <h2>
                  Earth event map
                </h2>
              </div>

              <div className="panel-heading__meta">
                <Layers3 size={14} />

                {activeLayerCount}
                /4 layers
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
                wildfireEvents={
                  wildfireData
                    ?.events
                  ?? []
                }
                stormEvents={
                  stormData
                    ?.events
                  ?? []
                }
                earthquakesVisible={
                  earthquakesVisible
                }
                volcanoesVisible={
                  volcanoesVisible
                }
                wildfiresVisible={
                  wildfiresVisible
                }
                stormsVisible={
                  stormsVisible
                }
                selectedEarthquake={
                  selectedEarthquake
                }
                selectedVolcano={
                  selectedVolcano
                }
                selectedNaturalHazard={
                  selectedNaturalHazard
                }
                onSelectEarthquake={
                  handleSelectEarthquake
                }
                onSelectVolcano={
                  handleSelectVolcano
                }
                onSelectNaturalHazard={
                  handleSelectNaturalHazard
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
                </div>
              )}

              {selectedVolcano
                !== null && (
                <div className="event-inspector event-inspector--volcano">
                  <div className="event-inspector__header">
                    <div className="inspector-volcano">
                      <Mountain size={23} />
                    </div>

                    <div>
                      <span className="event-inspector__severity">
                        NASA EONET VOLCANO
                      </span>

                      <h3>
                        {selectedVolcano.title}
                      </h3>
                    </div>
                  </div>

                  <div className="event-inspector__grid">
                    <div>
                      <span>
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
                </div>
              )}

              {selectedNaturalHazard
                !== null && (
                <div
                  className={
                    selectedNaturalHazard
                      .hazardType
                      === "wildfires"
                      ? "event-inspector event-inspector--wildfire"
                      : "event-inspector event-inspector--storm"
                  }
                >
                  <div className="event-inspector__header">
                    <div
                      className={
                        selectedNaturalHazard
                          .hazardType
                          === "wildfires"
                          ? "inspector-hazard inspector-hazard--fire"
                          : "inspector-hazard inspector-hazard--storm"
                      }
                    >
                      {selectedNaturalHazard
                        .hazardType
                        === "wildfires"
                        ? (
                          <Flame size={23} />
                        )
                        : (
                          <CloudLightning size={23} />
                        )
                      }
                    </div>

                    <div>
                      <span className="event-inspector__severity">
                        {selectedNaturalHazard
                          .hazardType
                          === "wildfires"
                          ? "NASA EONET WILDFIRE"
                          : "NASA EONET SEVERE STORM"}
                      </span>

                      <h3>
                        {selectedNaturalHazard
                          .title}
                      </h3>
                    </div>
                  </div>

                  <div className="event-inspector__grid">
                    <div>
                      <span>
                        Observed
                      </span>

                      <strong>
                        {formatDateTime(
                          selectedNaturalHazard
                            .observedAt,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Latitude
                      </span>

                      <strong>
                        {formatCoordinate(
                          selectedNaturalHazard
                            .latitude,
                          "N",
                          "S",
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Longitude
                      </span>

                      <strong>
                        {formatCoordinate(
                          selectedNaturalHazard
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
                        {selectedNaturalHazard
                          .observationCount}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Magnitude
                      </span>

                      <strong>
                        {selectedNaturalHazard
                          .magnitudeValue
                          === null
                          ? "Not reported"
                          : `${selectedNaturalHazard.magnitudeValue} ${selectedNaturalHazard.magnitudeUnit}`}
                      </strong>
                    </div>

                    <div>
                      <span>
                        Source
                      </span>

                      <strong>
                        {selectedNaturalHazard
                          .primarySource}
                      </strong>
                    </div>
                  </div>

                  <div className="event-inspector__footer">
                    <span>
                      EONET event
                    </span>

                    <code>
                      {selectedNaturalHazard.id}
                    </code>

                    {(selectedNaturalHazard
                      .sourceUrl
                      || selectedNaturalHazard
                        .detailUrl) && (
                      <a
                        href={
                          selectedNaturalHazard
                            .sourceUrl
                          || selectedNaturalHazard
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
                {currentFeedCount}
              </div>
            </div>

            <div className="feed-tabs feed-tabs--four">
              {(
                [
                  [
                    "earthquakes",
                    "Quakes",
                  ],
                  [
                    "volcanoes",
                    "Volcanoes",
                  ],
                  [
                    "wildfires",
                    "Fires",
                  ],
                  [
                    "severe-storms",
                    "Storms",
                  ],
                ] as const
              ).map(
                ([
                  mode,
                  label,
                ]) => (
                  <button
                    type="button"
                    key={mode}
                    className={
                      feedMode === mode
                        ? `feed-tab feed-tab--active feed-tab--${mode}`
                        : "feed-tab"
                    }
                    onClick={() =>
                      switchFeed(mode)
                    }
                  >
                    {label}
                  </button>
                ),
              )}
            </div>

            <div className="feed-summary">
              <span>
                Showing{" "}
                <strong>
                  {currentFeedCount}
                </strong>
              </span>

              <span>
                Source{" "}
                <strong>
                  {feedMode
                    === "earthquakes"
                    ? "USGS"
                    : "EONET"}
                </strong>
              </span>

              <span>
                Layers{" "}
                <strong>
                  {activeLayerCount}/4
                </strong>
              </span>
            </div>

            {loading
              && earthquakeData === null
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
                : feedMode
                  === "volcanoes"
                  ? (
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
                  : (
                    <HazardEventList
                      events={
                        feedMode
                          === "wildfires"
                          ? (
                              wildfireData
                                ?.events
                              ?? []
                            )
                          : (
                              stormData
                                ?.events
                              ?? []
                            )
                      }
                      selectedEvent={
                        selectedNaturalHazard
                      }
                      onSelect={
                        handleSelectNaturalHazard
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

        <SimulationPanel
          open={simulationOpen}
          onClose={() =>
            setSimulationOpen(false)
          }
        />

        <RegionalIntelligencePanel
          open={regionalOpen}
          onClose={() =>
            setRegionalOpen(false)
          }
        />

        <AlertCenterPanel
          open={alertCenterOpen}
          onClose={() =>
            setAlertCenterOpen(false)
          }
        />

        <WatchlistPanel
          open={watchlistOpen}
          refreshToken={
            watchlistRefreshCounter
          }
          onClose={() =>
            setWatchlistOpen(false)
          }
        />

        {watchlistMessage !== null && (
          <div className="watchlist-toast">
            <BookmarkPlus size={15} />

            <span>
              {watchlistMessage}
            </span>

            <button
              type="button"
              aria-label="Dismiss message"
              onClick={() =>
                setWatchlistMessage(null)
              }
            >
              ×
            </button>
          </div>
        )}

        <footer className="application-footer">
          <div>
            <Activity size={14} />

            <span>
              TerraScope Observatory
            </span>
          </div>

          <div>
            Seismic: USGS
          </div>

          <div>
            Natural hazards: NASA EONET
          </div>

          <div>
            Map: OpenStreetMap contributors
          </div>
        </footer>
      </main>
    </div>
  );
}
