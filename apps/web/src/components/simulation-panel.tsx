"use client";

import {
  Activity,
  Building2,
  CloudLightning,
  Flame,
  Gauge,
  Mountain,
  Play,
  RefreshCw,
  Users,
  WalletCards,
  X,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  fetchSimulationHistory,
  runImpactSimulation,
} from "@/lib/api";

import type {
  ImpactSimulationRequest,
  ImpactSimulationRunResponse,
  SimulationHazardType,
  SimulationHistoryResponse,
  SimulationRiskBand,
} from "@/types/simulation";

type SimulationPanelProps = {
  open: boolean;
  onClose: () => void;
};

const initialRequest: ImpactSimulationRequest = {
  name: "Urban impact scenario",
  hazardType: "earthquakes",
  latitude: 36.8065,
  longitude: 10.1815,
  radiusKm: 120,
  intensity: 7.5,
  exposedPopulation: 1500000,
  infrastructureResiliencePct: 55,
  emergencyReadinessPct: 65,
};

function numberLabel(
  value: number,
): string {
  return new Intl.NumberFormat(
    "en-US",
    {
      maximumFractionDigits: 0,
    },
  ).format(value);
}

function riskClass(
  riskBand: SimulationRiskBand,
): string {
  return (
    `simulation-risk simulation-risk--${riskBand.toLowerCase()}`
  );
}

function hazardLabel(
  type: SimulationHazardType,
): string {
  switch (type) {
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

export function SimulationPanel({
  open,
  onClose,
}: SimulationPanelProps) {
  const [
    request,
    setRequest,
  ] =
    useState<ImpactSimulationRequest>(
      initialRequest,
    );

  const [
    history,
    setHistory,
  ] =
    useState<
      SimulationHistoryResponse | null
    >(null);

  const [
    latest,
    setLatest,
  ] =
    useState<
      ImpactSimulationRunResponse | null
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

  const loadHistory = () => {
    setHistoryLoading(true);

    void fetchSimulationHistory()
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
              : "Unable to load simulation history.",
          );
        },
      )
      .finally(() => {
        setHistoryLoading(false);
      });
  };

  useEffect(() => {
    if (!open) {
      return;
    }

    const controller =
      new AbortController();

    void fetchSimulationHistory(
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
              : "Unable to load simulation history.",
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

  const handleSubmit = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setLoading(true);
    setError(null);

    void runImpactSimulation(
      request,
    )
      .then((response) => {
        setLatest(response);

        setHistory((current) => {
          if (current === null) {
            return null;
          }

          return {
            ...current,
            totalCount:
              current.totalCount + 1,
            runs: [
              {
                scenario:
                  response.scenario,
                result:
                  response.result,
              },
              ...current.runs,
            ].slice(0, 100),
          };
        });
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to run simulation.",
          );
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  if (!open) {
    return null;
  }

  return (
    <div className="simulation-overlay">
      <button
        type="button"
        className="simulation-backdrop"
        aria-label="Close simulations"
        onClick={onClose}
      />

      <section className="simulation-workspace">
        <header className="simulation-header">
          <div>
            <span className="panel-kicker">
              IMPACT LAB
            </span>

            <h2>
              Disaster simulation
            </h2>

            <p>
              Deterministic local impact
              modelling for portfolio
              demonstration.
            </p>
          </div>

          <button
            className="icon-button"
            type="button"
            aria-label="Close simulations"
            onClick={onClose}
          >
            <X size={18} />
          </button>
        </header>

        <div className="simulation-body">
          <form
            className="simulation-form"
            onSubmit={handleSubmit}
          >
            <div className="simulation-section-heading">
              <Gauge size={15} />

              <span>
                Scenario definition
              </span>
            </div>

            <label className="simulation-field simulation-field--wide">
              <span>
                Scenario name
              </span>

              <input
                value={request.name}
                maxLength={160}
                required
                onChange={(event) =>
                  setRequest({
                    ...request,
                    name:
                      event.target.value,
                  })
                }
              />
            </label>

            <label className="simulation-field">
              <span>
                Hazard
              </span>

              <select
                value={
                  request.hazardType
                }
                onChange={(event) =>
                  setRequest({
                    ...request,
                    hazardType:
                      event.target.value as SimulationHazardType,
                  })
                }
              >
                <option value="earthquakes">
                  Earthquake
                </option>

                <option value="volcanoes">
                  Volcano
                </option>

                <option value="wildfires">
                  Wildfire
                </option>

                <option value="severe-storms">
                  Severe storm
                </option>
              </select>
            </label>

            <label className="simulation-field">
              <span>
                Intensity
              </span>

              <input
                type="number"
                min={0}
                max={10}
                step={0.1}
                value={
                  request.intensity
                }
                onChange={(event) =>
                  setRequest({
                    ...request,
                    intensity:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="simulation-field">
              <span>
                Latitude
              </span>

              <input
                type="number"
                min={-90}
                max={90}
                step={0.0001}
                value={
                  request.latitude
                }
                onChange={(event) =>
                  setRequest({
                    ...request,
                    latitude:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="simulation-field">
              <span>
                Longitude
              </span>

              <input
                type="number"
                min={-180}
                max={180}
                step={0.0001}
                value={
                  request.longitude
                }
                onChange={(event) =>
                  setRequest({
                    ...request,
                    longitude:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="simulation-field">
              <span>
                Impact radius
              </span>

              <div className="simulation-input-unit">
                <input
                  type="number"
                  min={0.1}
                  max={2000}
                  step={1}
                  value={
                    request.radiusKm
                  }
                  onChange={(event) =>
                    setRequest({
                      ...request,
                      radiusKm:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />

                <small>
                  km
                </small>
              </div>
            </label>

            <label className="simulation-field">
              <span>
                Exposed population
              </span>

              <input
                type="number"
                min={0}
                max={500000000}
                step={1000}
                value={
                  request.exposedPopulation
                }
                onChange={(event) =>
                  setRequest({
                    ...request,
                    exposedPopulation:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label className="simulation-field">
              <span>
                Infrastructure resilience
              </span>

              <div className="simulation-input-unit">
                <input
                  type="number"
                  min={0}
                  max={100}
                  step={1}
                  value={
                    request.infrastructureResiliencePct
                  }
                  onChange={(event) =>
                    setRequest({
                      ...request,
                      infrastructureResiliencePct:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />

                <small>
                  %
                </small>
              </div>
            </label>

            <label className="simulation-field">
              <span>
                Emergency readiness
              </span>

              <div className="simulation-input-unit">
                <input
                  type="number"
                  min={0}
                  max={100}
                  step={1}
                  value={
                    request.emergencyReadinessPct
                  }
                  onChange={(event) =>
                    setRequest({
                      ...request,
                      emergencyReadinessPct:
                        Number(
                          event.target.value,
                        ),
                    })
                  }
                />

                <small>
                  %
                </small>
              </div>
            </label>

            <button
              className="simulation-run-button"
              type="submit"
              disabled={loading}
            >
              <Play size={15} />

              {loading
                ? "Running model..."
                : "Run impact model"}
            </button>
          </form>

          <div className="simulation-results">
            <div className="simulation-section-heading">
              <Activity size={15} />

              <span>
                Model result
              </span>
            </div>

            {error !== null && (
              <div className="simulation-error">
                {error}
              </div>
            )}

            {latest === null
              ? (
                <div className="simulation-result-empty">
                  <Gauge size={28} />

                  <strong>
                    Configure a scenario
                  </strong>

                  <span>
                    Run the local model to
                    generate population,
                    infrastructure and economic
                    impact estimates.
                  </span>
                </div>
              )
              : (
                <>
                  <div className="simulation-result-hero">
                    <div>
                      <span>
                        Composite risk
                      </span>

                      <strong>
                        {latest.result
                          .compositeRiskScore
                          .toFixed(1)}
                      </strong>
                    </div>

                    <span
                      className={
                        riskClass(
                          latest.result
                            .riskBand,
                        )
                      }
                    >
                      {latest.result
                        .riskBand}
                    </span>
                  </div>

                  <div className="simulation-metric-grid">
                    <div>
                      <Users size={17} />

                      <span>
                        Affected
                      </span>

                      <strong>
                        {numberLabel(
                          latest.result
                            .affectedPopulation,
                        )}
                      </strong>
                    </div>

                    <div>
                      <Users size={17} />

                      <span>
                        Displaced
                      </span>

                      <strong>
                        {numberLabel(
                          latest.result
                            .displacedPopulation,
                        )}
                      </strong>
                    </div>

                    <div>
                      <WalletCards
                        size={17}
                      />

                      <span>
                        Economic loss
                      </span>

                      <strong>
                        $
                        {latest.result
                          .estimatedEconomicLossMusd
                          .toFixed(1)}
                        M
                      </strong>
                    </div>

                    <div>
                      <Building2
                        size={17}
                      />

                      <span>
                        Infrastructure
                      </span>

                      <strong>
                        {latest.result
                          .infrastructureDisruptionPct
                          .toFixed(1)}
                        %
                      </strong>
                    </div>

                    <div>
                      <Activity size={17} />

                      <span>
                        Response pressure
                      </span>

                      <strong>
                        {latest.result
                          .responsePressurePct
                          .toFixed(1)}
                        %
                      </strong>
                    </div>

                    <div>
                      {latest.scenario
                        .hazardType
                        === "earthquakes"
                        ? (
                          <Activity
                            size={17}
                          />
                        )
                        : latest.scenario
                          .hazardType
                          === "volcanoes"
                          ? (
                            <Mountain
                              size={17}
                            />
                          )
                          : latest.scenario
                            .hazardType
                            === "wildfires"
                            ? (
                              <Flame
                                size={17}
                              />
                            )
                            : (
                              <CloudLightning
                                size={17}
                              />
                            )
                      }

                      <span>
                        Hazard
                      </span>

                      <strong>
                        {hazardLabel(
                          latest.scenario
                            .hazardType,
                        )}
                      </strong>
                    </div>
                  </div>

                  <div className="simulation-methodology">
                    <span>
                      Methodology
                    </span>

                    <code>
                      {latest.result
                        .methodologyVersion}
                    </code>
                  </div>
                </>
              )
            }

            <div className="simulation-history-heading">
              <div>
                <span className="panel-kicker">
                  RUN HISTORY
                </span>

                <strong>
                  Previous simulations
                </strong>
              </div>

              <button
                type="button"
                onClick={loadHistory}
                disabled={historyLoading}
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
              <div className="simulation-history-summary">
                <span>
                  Runs{" "}
                  <strong>
                    {history.totalCount}
                  </strong>
                </span>

                <span>
                  Severe{" "}
                  <strong>
                    {history.severeCount}
                  </strong>
                </span>

                <span>
                  Extreme{" "}
                  <strong>
                    {history.extremeCount}
                  </strong>
                </span>

                <span>
                  Avg risk{" "}
                  <strong>
                    {history.averageRiskScore
                      .toFixed(1)}
                  </strong>
                </span>
              </div>
            )}

            <div className="simulation-history-list">
              {historyLoading
                && history === null
                ? (
                  <div className="simulation-history-empty">
                    Loading simulation history…
                  </div>
                )
                : history === null
                  || history.runs.length === 0
                  ? (
                    <div className="simulation-history-empty">
                      No simulation runs yet.
                    </div>
                  )
                  : history.runs.map(
                    (item) => (
                      <article
                        className="simulation-history-card"
                        key={
                          item.result.id
                        }
                      >
                        <div>
                          <span>
                            {hazardLabel(
                              item.scenario
                                .hazardType,
                            )}
                          </span>

                          <strong>
                            {item.scenario.name}
                          </strong>
                        </div>

                        <div>
                          <span
                            className={
                              riskClass(
                                item.result
                                  .riskBand,
                              )
                            }
                          >
                            {item.result
                              .riskBand}
                          </span>

                          <strong>
                            {item.result
                              .compositeRiskScore
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
