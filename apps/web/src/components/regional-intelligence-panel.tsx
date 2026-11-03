"use client";

import {
  Activity,
  CloudLightning,
  Flame,
  LocateFixed,
  MapPin,
  Mountain,
  Radar,
  RefreshCw,
  Search,
  X,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useRef,
  useState,
} from "react";

import {
  useDialogBehavior,
} from "@/lib/use-dialog-behavior";

import {
  analyzeRegionalIntelligence,
  fetchEarthquakes,
  fetchNaturalHazards,
  fetchRegionalAnalysisHistory,
  fetchVolcanoes,
} from "@/lib/api";

import type {
  RegionalActivityBand,
  RegionalAnalysisHistoryResponse,
  RegionalAnalysisRequest,
  RegionalAnalysisResult,
  RegionalHazardPoint,
  RegionalHazardType,
} from "@/types/regional-intelligence";

export type RegionalPreset = {
  regionName: string;
  latitude: number;
  longitude: number;
  radiusKm: number;
  nonce: number;
};

type RegionalIntelligencePanelProps = {
  open: boolean;
  onClose: () => void;
  preset?: RegionalPreset | null;
};

type RegionalFormState =
  Omit<
    RegionalAnalysisRequest,
    "points"
  >;

const initialForm: RegionalFormState = {
  regionName:
    "Regional analysis",
  latitude: 36.8065,
  longitude: 10.1815,
  radiusKm: 500,
};

function activityClass(
  band: RegionalActivityBand,
): string {
  return (
    `regional-band regional-band--${band.toLowerCase()}`
  );
}

function hazardLabel(
  hazardType: RegionalHazardType,
): string {
  switch (hazardType) {
    case "earthquakes":
      return "Earthquake";

    case "volcanoes":
      return "Volcano";

    case "wildfires":
      return "Wildfire";

    case "severe-storms":
      return "Severe storm";
  }
}

function HazardIcon({
  hazardType,
}: {
  hazardType: RegionalHazardType;
}) {
  if (hazardType === "earthquakes") {
    return <Activity size={16} />;
  }

  if (hazardType === "volcanoes") {
    return <Mountain size={16} />;
  }

  if (hazardType === "wildfires") {
    return <Flame size={16} />;
  }

  return <CloudLightning size={16} />;
}

export function RegionalIntelligencePanel({
  open,
  onClose,
  preset = null,
}: RegionalIntelligencePanelProps) {
  const [
    form,
    setForm,
  ] =
    useState<RegionalFormState>(
      initialForm,
    );

  const [
    result,
    setResult,
  ] =
    useState<
      RegionalAnalysisResult | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      RegionalAnalysisHistoryResponse | null
    >(null);

  const [
    loading,
    setLoading,
  ] =
    useState(false);

  const [
    historyLoading,
    setHistoryLoading,
  ] =
    useState(true);

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  const [
    appliedPresetNonce,
    setAppliedPresetNonce,
  ] =
    useState<number | null>(
      null,
    );

  if (
    open
    && preset !== null
    && preset.nonce !== appliedPresetNonce
  ) {
    setAppliedPresetNonce(
      preset.nonce,
    );

    setForm({
      regionName:
        preset.regionName,
      latitude:
        preset.latitude,
      longitude:
        preset.longitude,
      radiusKm:
        preset.radiusKm,
    });
  }

  useEffect(() => {
    if (!open) {
      return;
    }

    const controller =
      new AbortController();

    void fetchRegionalAnalysisHistory(
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setHistory(response);
        setError(null);
      })
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
              : "Unable to load regional history.",
          );
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setHistoryLoading(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [
    open,
  ]);

  const loadHistory = () => {
    setHistoryLoading(true);

    void fetchRegionalAnalysisHistory()
      .then((response) => {
        setHistory(response);
        setError(null);
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to refresh regional history.",
          );
        },
      )
      .finally(() => {
        setHistoryLoading(false);
      });
  };

  const loadLivePoints =
    async (): Promise<
      RegionalHazardPoint[]
    > => {
      const [
        earthquakes,
        volcanoes,
        wildfires,
        storms,
      ] =
        await Promise.all([
          fetchEarthquakes({
            period: "week",
            minMagnitude: 2.5,
            maxDepth: 1000,
            search: "",
            sort: "time",
            limit: 500,
          }),

          fetchVolcanoes({
            status: "open",
            days: 3650,
            limit: 500,
            search: "",
          }),

          fetchNaturalHazards({
            type: "wildfires",
            status: "open",
            days: 365,
            limit: 500,
            search: "",
          }),

          fetchNaturalHazards({
            type: "severe-storms",
            status: "open",
            days: 365,
            limit: 500,
            search: "",
          }),
        ]);

      const points:
        RegionalHazardPoint[] = [
          ...earthquakes.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "earthquakes" as const,
              title: event.place,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitude,
              observedAt:
                event.occurredAt,
            }),
          ),

          ...volcanoes.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "volcanoes" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity: null,
              observedAt:
                event.observedAt,
            }),
          ),

          ...wildfires.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "wildfires" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitudeValue,
              observedAt:
                event.observedAt,
            }),
          ),

          ...storms.events.map(
            (event) => ({
              id: event.id,
              hazardType:
                "severe-storms" as const,
              title: event.title,
              latitude:
                event.latitude,
              longitude:
                event.longitude,
              intensity:
                event.magnitudeValue,
              observedAt:
                event.observedAt,
            }),
          ),
        ];

      return points.slice(
        0,
        2000,
      );
    };

  const handleAnalyze = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setLoading(true);
    setError(null);

    void loadLivePoints()
      .then((points) =>
        analyzeRegionalIntelligence({
          ...form,
          points,
        }),
      )
      .then((response) => {
        setResult(response);

        setHistory(
          (current) => {
            if (current === null) {
              return null;
            }

            return {
              ...current,
              totalCount:
                current.totalCount + 1,
              runs: [
                response,
                ...current.runs,
              ].slice(
                0,
                100,
              ),
            };
          },
        );
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to analyze the region.",
          );
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const dialogRef = useRef<HTMLElement | null>(null);

  useDialogBehavior({ open, onClose, containerRef: dialogRef });

  if (!open) {
    return null;
  }

  return (
    <div className="regional-overlay">
      <button
        type="button"
        className="regional-backdrop"
        aria-label="Close regional intelligence"
        tabIndex={-1}
        onClick={onClose}
      />

      <section
        ref={dialogRef}
        className="regional-workspace"
        role="dialog"
        aria-modal="true"
        aria-labelledby="regional-title"
        tabIndex={-1}
      >
        <header className="regional-header">
          <div>
            <span className="panel-kicker">
              SPATIAL INTELLIGENCE
            </span>

            <h2 id="regional-title">
              Regional hazard intelligence
            </h2>

            <p>
              Radius-based analysis across
              the current USGS and EONET
              hazard feeds.
            </p>
          </div>

          <button
            className="icon-button"
            type="button"
            aria-label="Close regional intelligence"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="regional-body">
          <form
            className="regional-form"
            onSubmit={handleAnalyze}
          >
            <div className="regional-section-heading">
              <LocateFixed size={15} />

              <span>
                Analysis area
              </span>
            </div>

            <label className="regional-field regional-field--wide">
              <span>
                Region label
              </span>

              <input
                required
                maxLength={160}
                value={
                  form.regionName
                }
                onChange={(event) =>
                  setForm({
                    ...form,
                    regionName:
                      event.target.value,
                  })
                }
              />
            </label>

            <label className="regional-field">
              <span>
                Latitude
              </span>

              <input
                type="number"
                min={-90}
                max={90}
                step={0.0001}
                value={
                  form.latitude
                }
                onChange={(event) =>
                  setForm({
                    ...form,
                    latitude:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="regional-field">
              <span>
                Longitude
              </span>

              <input
                type="number"
                min={-180}
                max={180}
                step={0.0001}
                value={
                  form.longitude
                }
                onChange={(event) =>
                  setForm({
                    ...form,
                    longitude:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="regional-field regional-field--wide">
              <span>
                Radius
              </span>

              <div className="regional-radius-row">
                <input
                  type="number"
                  min={1}
                  max={2000}
                  step={10}
                  value={
                    form.radiusKm
                  }
                  onChange={(event) =>
                    setForm({
                      ...form,
                      radiusKm:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />

                <span>
                  km
                </span>
              </div>
            </label>

            <div className="regional-presets">
              {[
                100,
                250,
                500,
                1000,
              ].map(
                (radius) => (
                  <button
                    key={radius}
                    type="button"
                    onClick={() =>
                      setForm({
                        ...form,
                        radiusKm:
                          radius,
                      })
                    }
                  >
                    {radius} km
                  </button>
                ),
              )}
            </div>

            <button
              className="regional-analyze-button"
              type="submit"
              disabled={loading}
            >
              <Search size={15} />

              {loading
                ? "Loading live hazards..."
                : "Analyze regional activity"}
            </button>

            <div className="regional-methodology-note">
              <Radar size={14} />

              <span>
                The regional activity index
                summarizes nearby monitored
                hazard activity. It is not an
                official forecast or emergency
                risk classification.
              </span>
            </div>
          </form>

          <div className="regional-results">
            <div className="regional-section-heading">
              <Radar size={15} />

              <span>
                Regional intelligence
              </span>
            </div>

            {error !== null && (
              <div className="regional-error">
                {error}
              </div>
            )}

            {result === null
              ? (
                <div className="regional-empty">
                  <MapPin size={30} />

                  <strong>
                    Select an analysis area
                  </strong>

                  <span>
                    TerraScope will merge the
                    active hazard feeds and
                    calculate spatial activity
                    within the selected radius.
                  </span>
                </div>
              )
              : (
                <>
                  <div className="regional-hero">
                    <div>
                      <span>
                        Activity index
                      </span>

                      <strong>
                        {result.activityIndex
                          .toFixed(1)}
                      </strong>
                    </div>

                    <span
                      className={
                        activityClass(
                          result.activityBand,
                        )
                      }
                    >
                      {result.activityBand}
                    </span>
                  </div>

                  <div className="regional-metrics">
                    <div>
                      <Radar size={16} />

                      <span>
                        Matched
                      </span>

                      <strong>
                        {result.matchedEventCount}
                      </strong>
                    </div>

                    <div>
                      <Activity size={16} />

                      <span>
                        Source points
                      </span>

                      <strong>
                        {result.sourcePointCount}
                      </strong>
                    </div>

                    <div>
                      <MapPin size={16} />

                      <span>
                        Avg distance
                      </span>

                      <strong>
                        {result.averageDistanceKm
                          .toFixed(1)}
                        {" "}km
                      </strong>
                    </div>

                    <div>
                      <LocateFixed
                        size={16}
                      />

                      <span>
                        Radius
                      </span>

                      <strong>
                        {result.radiusKm
                          .toFixed(0)}
                        {" "}km
                      </strong>
                    </div>
                  </div>

                  <div className="regional-breakdown">
                    <div>
                      <Activity size={15} />

                      <span>
                        Earthquakes
                      </span>

                      <strong>
                        {result.breakdown
                          .earthquakeCount}
                      </strong>
                    </div>

                    <div>
                      <Mountain size={15} />

                      <span>
                        Volcanoes
                      </span>

                      <strong>
                        {result.breakdown
                          .volcanoCount}
                      </strong>
                    </div>

                    <div>
                      <Flame size={15} />

                      <span>
                        Wildfires
                      </span>

                      <strong>
                        {result.breakdown
                          .wildfireCount}
                      </strong>
                    </div>

                    <div>
                      <CloudLightning
                        size={15}
                      />

                      <span>
                        Storms
                      </span>

                      <strong>
                        {result.breakdown
                          .severeStormCount}
                      </strong>
                    </div>
                  </div>

                  <div className="regional-nearest">
                    <span className="panel-kicker">
                      NEAREST MONITORED EVENT
                    </span>

                    {result.nearestEvent
                      === null
                      ? (
                        <p>
                          No monitored event
                          falls inside this
                          radius.
                        </p>
                      )
                      : (
                        <div>
                          <span className="regional-nearest__icon">
                            <HazardIcon
                              hazardType={
                                result.nearestEvent
                                  .hazardType
                              }
                            />
                          </span>

                          <div>
                            <small>
                              {hazardLabel(
                                result.nearestEvent
                                  .hazardType,
                              )}
                            </small>

                            <strong>
                              {result.nearestEvent
                                .title}
                            </strong>
                          </div>

                          <span className="regional-nearest__distance">
                            {result.nearestEvent
                              .distanceKm
                              .toFixed(1)}
                            {" "}km
                          </span>
                        </div>
                      )
                    }
                  </div>
                </>
              )
            }

            <div className="regional-history-heading">
              <div>
                <span className="panel-kicker">
                  ANALYSIS HISTORY
                </span>

                <strong>
                  Regional snapshots
                </strong>
              </div>

              <button
                type="button"
                disabled={historyLoading}
                onClick={loadHistory}
              >
                <RefreshCw
                  size={13}
                  className={
                    historyLoading
                      ? "spin"
                      : ""
                  }
                />

                Refresh
              </button>
            </div>

            {history !== null && (
              <div className="regional-history-summary">
                <span>
                  Runs
                  <strong>
                    {history.totalCount}
                  </strong>
                </span>

                <span>
                  High
                  <strong>
                    {history.highCount}
                  </strong>
                </span>

                <span>
                  Critical
                  <strong>
                    {history.criticalCount}
                  </strong>
                </span>

                <span>
                  Avg index
                  <strong>
                    {history.averageActivityIndex
                      .toFixed(1)}
                  </strong>
                </span>
              </div>
            )}

            <div className="regional-history-list">
              {historyLoading
                && history === null
                ? (
                  <div className="regional-history-empty">
                    Loading history…
                  </div>
                )
                : history === null
                  || history.runs.length === 0
                  ? (
                    <div className="regional-history-empty">
                      No regional analyses yet.
                    </div>
                  )
                  : history.runs.map(
                    (run) => (
                      <article
                        className="regional-history-card"
                        key={run.id}
                      >
                        <div>
                          <span>
                            {run.radiusKm
                              .toFixed(0)}
                            {" "}km radius
                          </span>

                          <strong>
                            {run.regionName}
                          </strong>
                        </div>

                        <div>
                          <span
                            className={
                              activityClass(
                                run.activityBand,
                              )
                            }
                          >
                            {run.activityBand}
                          </span>

                          <strong>
                            {run.activityIndex
                              .toFixed(1)}
                          </strong>
                        </div>
                      </article>
                    ),
                  )
              }
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
