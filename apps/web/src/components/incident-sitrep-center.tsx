"use client";

import {
  Archive,
  Clock3,
  FileText,
  Printer,
  RefreshCw,
  ShieldAlert,
  TrendingUp,
} from "lucide-react";

import {
  useEffect,
  useState,
} from "react";

import {
  createIncidentSituationReport,
  fetchIncidentSitrepHistory,
  fetchIncidentSitrepPreview,
} from "@/lib/api";

import type {
  IncidentSitrepHistoryResponse,
  IncidentSitrepPreview,
} from "@/types/sitrep";

type IncidentSitrepCenterProps = {
  incidentId: string;
  onTimelineChanged: () => void;
};

function formatTimestamp(
  value: string,
): string {
  return new Intl.DateTimeFormat(
    "en",
    {
      dateStyle: "medium",
      timeStyle: "short",
    },
  ).format(
    new Date(value),
  );
}

export function IncidentSitrepCenter({
  incidentId,
  onTimelineChanged,
}: IncidentSitrepCenterProps) {
  const [
    preview,
    setPreview,
  ] =
    useState<
      IncidentSitrepPreview | null
    >(null);

  const [
    history,
    setHistory,
  ] =
    useState<
      IncidentSitrepHistoryResponse | null
    >(null);

  const [
    loading,
    setLoading,
  ] =
    useState(true);

  const [
    busy,
    setBusy,
  ] =
    useState(false);

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    );

  useEffect(() => {
    const controller =
      new AbortController();

    void Promise.all([
      fetchIncidentSitrepPreview(
        incidentId,
        controller.signal,
      ),
      fetchIncidentSitrepHistory(
        incidentId,
        controller.signal,
      ),
    ])
      .then(
        ([
          previewResponse,
          historyResponse,
        ]) => {
          if (
            controller.signal.aborted
          ) {
            return;
          }

          setPreview(
            previewResponse,
          );

          setHistory(
            historyResponse,
          );

          setError(null);
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
              : "Unable to load SITREP center.",
          );
        },
      )
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setLoading(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [
    incidentId,
  ]);

  const refresh = () => {
    setLoading(true);

    return Promise.all([
      fetchIncidentSitrepPreview(
        incidentId,
      ),
      fetchIncidentSitrepHistory(
        incidentId,
      ),
    ])
      .then(
        ([
          previewResponse,
          historyResponse,
        ]) => {
          setPreview(
            previewResponse,
          );

          setHistory(
            historyResponse,
          );

          setError(null);
        },
      )
      .finally(() => {
        setLoading(false);
      });
  };

  const captureReport = () => {
    setBusy(true);
    setError(null);

    void createIncidentSituationReport(
      incidentId,
    )
      .then(() => {
        onTimelineChanged();

        return refresh();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to capture situation report.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  return (
    <section className="sitrep-center">
      <div className="sitrep-heading">
        <div>
          <span className="panel-kicker">
            OPERATIONAL BRIEFING
          </span>

          <strong>
            Situation Report Center
          </strong>
        </div>

        <div className="sitrep-actions">
          <button
            type="button"
            disabled={
              loading
              || busy
            }
            onClick={() => {
              void refresh()
                .catch(
                  (
                    caughtError:
                      unknown,
                  ) => {
                    setError(
                      caughtError
                        instanceof Error
                        ? caughtError.message
                        : "Unable to refresh SITREP.",
                    );
                  },
                );
            }}
          >
            <RefreshCw
              size={13}
            />

            Refresh
          </button>

          <button
            type="button"
            disabled={
              loading
              || busy
              || preview === null
            }
            onClick={
              captureReport
            }
          >
            <Archive
              size={13}
            />

            Capture
          </button>

          <button
            type="button"
            disabled={
              preview === null
            }
            onClick={() => {
              window.print();
            }}
          >
            <Printer
              size={13}
            />

            Print
          </button>
        </div>
      </div>

      {error !== null && (
        <div className="sitrep-error">
          {error}
        </div>
      )}

      {loading
        && preview === null
        ? (
          <div className="sitrep-empty">
            Generating operational briefing…
          </div>
        )
        : preview !== null && (
          <>
            <div className="sitrep-report-header">
              <div>
                <FileText
                  size={18}
                />

                <div>
                  <span>
                    CURRENT SITREP
                  </span>

                  <strong>
                    {preview.incidentTitle}
                  </strong>
                </div>
              </div>

              <div>
                <span className="sitrep-incident-state">
                  {preview.incidentStatus}
                </span>

                <span className="sitrep-incident-severity">
                  {preview.incidentSeverity}
                </span>

                <span className="sitrep-command-pressure">
                  {preview.commandBand}
                  {" · "}
                  {preview.commandScore}
                  /100
                </span>
              </div>
            </div>

            <div className="sitrep-generated">
              <Clock3
                size={12}
              />

              Generated
              {" "}
              {formatTimestamp(
                preview.generatedAt,
              )}
            </div>

            <section className="sitrep-executive-summary">
              <span>
                EXECUTIVE SUMMARY
              </span>

              <p>
                {preview.executiveSummary}
              </p>
            </section>

            <div className="sitrep-metrics">
              <div>
                <span>
                  Open actions
                </span>

                <strong>
                  {preview.openActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Overdue / blocked
                </span>

                <strong>
                  {preview.overdueActionCount}
                  {" / "}
                  {preview.blockedActionCount}
                </strong>
              </div>

              <div>
                <span>
                  Resource shortages
                </span>

                <strong>
                  {preview.resourceShortageCount}
                </strong>
              </div>

              <div>
                <span>
                  Evacuation backlog
                </span>

                <strong>
                  {preview.evacuationRemaining}
                </strong>
              </div>

              <div>
                <span>
                  Lifelines disrupted
                </span>

                <strong>
                  {preview.lifelineDisruptedCount}
                </strong>
              </div>

              <div>
                <span>
                  Recovery backlog
                </span>

                <strong>
                  $
                  {preview
                    .unresolvedDamageLossMusd
                    .toFixed(1)}
                  M
                </strong>
              </div>
            </div>

            <section className="sitrep-priorities">
              <div className="sitrep-section-heading">
                <ShieldAlert
                  size={14}
                />

                <strong>
                  Command priorities
                </strong>
              </div>

              <div className="sitrep-priority-list">
                {preview.priorities.map(
                  (
                    priority,
                    index,
                  ) => (
                    <article
                      key={
                        `${index}-${priority}`
                      }
                    >
                      <span>
                        {index + 1}
                      </span>

                      <strong>
                        {priority}
                      </strong>
                    </article>
                  ),
                )}
              </div>
            </section>

            <section className="sitrep-history">
              <div className="sitrep-section-heading">
                <TrendingUp
                  size={14}
                />

                <strong>
                  Briefing history
                </strong>

                <span>
                  {history
                    ?.reports
                    .length
                    ?? 0}
                  {" reports"}
                </span>
              </div>

              {history === null
                || history
                  .reports
                  .length === 0
                ? (
                  <div className="sitrep-history-empty">
                    No persisted situation reports yet.
                  </div>
                )
                : (
                  <div className="sitrep-history-list">
                    {history
                      .reports
                      .slice(
                        0,
                        8,
                      )
                      .map(
                        (report) => (
                          <article
                            key={
                              report.id
                            }
                          >
                            <div>
                              <span>
                                SITREP
                              </span>

                              <strong>
                                #
                                {report.reportNumber}
                              </strong>
                            </div>

                            <div>
                              <span>
                                Command pressure
                              </span>

                              <strong>
                                {report.commandScore}
                                /100
                                {" · "}
                                {report.commandBand}
                              </strong>
                            </div>

                            <div>
                              <span>
                                Recovery backlog
                              </span>

                              <strong>
                                $
                                {report
                                  .unresolvedDamageLossMusd
                                  .toFixed(1)}
                                M
                              </strong>
                            </div>

                            <time>
                              {formatTimestamp(
                                report.createdAt,
                              )}
                            </time>
                          </article>
                        ),
                      )
                    }
                  </div>
                )
              }
            </section>
          </>
        )
      }
    </section>
  );
}
