"use client";

import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  ClipboardCheck,
  DollarSign,
  Hammer,
  RefreshCw,
  Trash2,
  UserRound,
  Wrench,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createDamageAssessment,
  deleteDamageAssessment,
  fetchDamageRecoveryBoard,
  updateDamageOwner,
  updateDamageProgress,
  updateDamageRecoveryEta,
  updateDamageSeverity,
  verifyDamageAssessment,
} from "@/lib/api";

import type {
  CreateDamageAssessmentRequest,
  DamageAssetCategory,
  DamageRecoveryBoardResponse,
  DamageSeverity,
} from "@/types/damage-recovery";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentDamageRecoveryBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialAssessment:
  CreateDamageAssessmentRequest = {
    category: "PUBLIC_BUILDING",
    assetName: "",
    description: "",
    severity: "MODERATE",
    affectedUnits: 1,
    estimatedLossMusd: 0,
    owner: "Unassigned",
    recoveryEta: null,
  };

function severityClass(
  severity:
    DamageSeverity,
): string {
  return (
    `damage-severity damage-severity--${severity.toLowerCase()}`
  );
}

function statusClass(
  status:
    "REPORTED"
    | "VERIFIED"
    | "REPAIRING"
    | "RESTORED",
): string {
  return (
    `damage-status damage-status--${status.toLowerCase()}`
  );
}

function categoryLabel(
  category:
    DamageAssetCategory,
): string {
  switch (category) {
    case "RESIDENTIAL":
      return "Residential";

    case "COMMERCIAL":
      return "Commercial";

    case "PUBLIC_BUILDING":
      return "Public building";

    case "ROAD":
      return "Road";

    case "BRIDGE":
      return "Bridge";

    case "UTILITY":
      return "Utility";

    case "HEALTHCARE":
      return "Healthcare";

    case "OTHER":
      return "Other";
  }
}

function formatEta(
  value: string | null,
): string {
  if (value === null) {
    return "No ETA";
  }

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

export function IncidentDamageRecoveryBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentDamageRecoveryBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      DamageRecoveryBoardResponse | null
    >(null);

  const [
    draft,
    setDraft,
  ] =
    useState(
      initialAssessment,
    );

  const [
    etaDraft,
    setEtaDraft,
  ] =
    useState("");

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

    void fetchDamageRecoveryBoard(
      incidentId,
      controller.signal,
    )
      .then((response) => {
        if (
          controller.signal.aborted
        ) {
          return;
        }

        setBoard(response);
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
              : "Unable to load damage recovery operations.",
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

  const applyMutation = (
    operation:
      Promise<DamageRecoveryBoardResponse>,
  ) => {
    setBusy(true);
    setError(null);

    void operation
      .then((response) => {
        setBoard(response);
        onTimelineChanged();
      })
      .catch(
        (caughtError: unknown) => {
          setError(
            caughtError
              instanceof Error
              ? caughtError.message
              : "Unable to update damage recovery operations.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleCreate = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      draft.assetName.trim()
      === ""
    ) {
      return;
    }

    let recoveryEta:
      string | null = null;

    if (
      etaDraft.trim()
      !== ""
    ) {
      recoveryEta =
        new Date(
          etaDraft,
        ).toISOString();
    }

    applyMutation(
      createDamageAssessment(
        incidentId,
        {
          ...draft,
          assetName:
            draft.assetName.trim(),
          description:
            draft.description.trim(),
          owner:
            draft.owner.trim()
            || "Unassigned",
          recoveryEta,
        },
      ),
    );

    setDraft(
      initialAssessment,
    );

    setEtaDraft("");
  };

  const readOnly =
    incidentStatus === "CLOSED";

  return (
    <section className="damage-board">
      <div className="damage-heading">
        <div>
          <span className="panel-kicker">
            RECOVERY OPERATIONS
          </span>

          <strong>
            Damage assessment
          </strong>
        </div>

        <button
          type="button"
          disabled={
            loading
            || busy
          }
          onClick={() => {
            setLoading(true);

            void fetchDamageRecoveryBoard(
              incidentId,
            )
              .then((response) => {
                setBoard(response);
                setError(null);
              })
              .catch(
                (
                  caughtError:
                    unknown,
                ) => {
                  setError(
                    caughtError
                      instanceof Error
                      ? caughtError.message
                      : "Unable to refresh damage recovery operations.",
                  );
                },
              )
              .finally(() => {
                setLoading(false);
              });
          }}
        >
          <RefreshCw size={13} />
          Refresh
        </button>
      </div>

      <div className="damage-summary">
        <div>
          <span>
            Assessments
          </span>

          <strong>
            {board?.totalCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Unverified
          </span>

          <strong>
            {board?.reportedCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Repairing
          </span>

          <strong>
            {board?.repairingCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Critical damage
          </span>

          <strong>
            {board?.criticalDamageCount ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Affected units
          </span>

          <strong>
            {board?.affectedUnits ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Recovery backlog
          </span>

          <strong>
            $
            {(
              board?.unresolvedEstimatedLossMusd
              ?? 0
            ).toFixed(1)}
            M
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="damage-error">
          {error}
        </div>
      )}

      {!readOnly && (
        <form
          className="damage-form"
          onSubmit={handleCreate}
        >
          <label>
            <span>
              Category
            </span>

            <select
              value={draft.category}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  category:
                    event.target.value as DamageAssetCategory,
                })
              }
            >
              <option value="RESIDENTIAL">
                Residential
              </option>

              <option value="COMMERCIAL">
                Commercial
              </option>

              <option value="PUBLIC_BUILDING">
                Public building
              </option>

              <option value="ROAD">
                Road
              </option>

              <option value="BRIDGE">
                Bridge
              </option>

              <option value="UTILITY">
                Utility
              </option>

              <option value="HEALTHCARE">
                Healthcare
              </option>

              <option value="OTHER">
                Other
              </option>
            </select>
          </label>

          <label>
            <span>
              Severity
            </span>

            <select
              value={draft.severity}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  severity:
                    event.target.value as DamageSeverity,
                })
              }
            >
              <option value="MINOR">
                Minor
              </option>

              <option value="MODERATE">
                Moderate
              </option>

              <option value="MAJOR">
                Major
              </option>

              <option value="DESTROYED">
                Destroyed
              </option>
            </select>
          </label>

          <label className="damage-form-wide">
            <span>
              Asset / location
            </span>

            <input
              required
              maxLength={300}
              value={draft.assetName}
              placeholder="e.g. North River Bridge"
              onChange={(event) =>
                setDraft({
                  ...draft,
                  assetName:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>
              Affected units
            </span>

            <input
              type="number"
              min={0}
              max={100000000}
              value={draft.affectedUnits}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  affectedUnits:
                    Number(
                      event.target.value,
                    ),
                })
              }
            />
          </label>

          <label>
            <span>
              Estimated loss ($M)
            </span>

            <input
              type="number"
              min={0}
              step="0.1"
              value={
                draft.estimatedLossMusd
              }
              onChange={(event) =>
                setDraft({
                  ...draft,
                  estimatedLossMusd:
                    Number(
                      event.target.value,
                    ),
                })
              }
            />
          </label>

          <label>
            <span>
              Recovery owner
            </span>

            <input
              maxLength={120}
              value={draft.owner}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  owner:
                    event.target.value,
                })
              }
            />
          </label>

          <label>
            <span>
              Recovery ETA
            </span>

            <input
              type="datetime-local"
              value={etaDraft}
              onChange={(event) =>
                setEtaDraft(
                  event.target.value,
                )
              }
            />
          </label>

          <label className="damage-form-wide">
            <span>
              Damage description
            </span>

            <textarea
              maxLength={2000}
              value={draft.description}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  description:
                    event.target.value,
                })
              }
            />
          </label>

          <button
            type="submit"
            className="damage-create"
            disabled={busy}
          >
            <ClipboardCheck size={13} />

            Report damage
          </button>
        </form>
      )}

      {readOnly && (
        <div className="damage-readonly">
          This incident is closed.
          Damage recovery operations are read-only.
        </div>
      )}

      <div className="damage-board-insight">
        <div>
          <DollarSign size={14} />

          <span>
            Total assessed loss
          </span>

          <strong>
            $
            {(
              board?.totalEstimatedLossMusd
              ?? 0
            ).toFixed(1)}
            M
          </strong>
        </div>

        <div>
          <Wrench size={14} />

          <span>
            Average repair progress
          </span>

          <strong>
            {(
              board?.averageRepairProgressPct
              ?? 0
            ).toFixed(1)}
            %
          </strong>
        </div>
      </div>

      <div className="damage-list">
        {loading
          && board === null
          ? (
            <div className="damage-empty">
              Loading damage assessments…
            </div>
          )
          : board === null
            || board.assessments.length
              === 0
            ? (
              <div className="damage-empty damage-empty--large">
                <Building2 size={28} />

                <strong>
                  No damage assessments
                </strong>

                <span>
                  Report damaged structures,
                  roads, utilities and public
                  infrastructure.
                </span>
              </div>
            )
            : board.assessments.map(
              (assessment) => {
                const restored =
                  assessment.status
                    === "RESTORED";

                return (
                  <article
                    key={assessment.id}
                    className={
                      restored
                        ? "damage-card damage-card--restored"
                        : assessment.severity === "DESTROYED"
                          || assessment.severity === "MAJOR"
                          ? "damage-card damage-card--critical"
                          : "damage-card"
                    }
                  >
                    <div className="damage-card-top">
                      <div>
                        <span
                          className={
                            severityClass(
                              assessment.severity,
                            )
                          }
                        >
                          {assessment.severity}
                        </span>

                        <span
                          className={
                            statusClass(
                              assessment.status,
                            )
                          }
                        >
                          {assessment.status}
                        </span>
                      </div>

                      {!readOnly && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            applyMutation(
                              deleteDamageAssessment(
                                incidentId,
                                assessment.id,
                              ),
                            )
                          }
                        >
                          <Trash2 size={13} />
                        </button>
                      )}
                    </div>

                    <div className="damage-card-title">
                      <span>
                        {categoryLabel(
                          assessment.category,
                        )}
                      </span>

                      <strong>
                        {assessment.assetName}
                      </strong>
                    </div>

                    {assessment.description
                      !== "" && (
                      <p>
                        {assessment.description}
                      </p>
                    )}

                    <div className="damage-card-metrics">
                      <div>
                        <span>
                          Units
                        </span>

                        <strong>
                          {assessment.affectedUnits}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Est. loss
                        </span>

                        <strong>
                          $
                          {assessment
                            .estimatedLossMusd
                            .toFixed(1)}
                          M
                        </strong>
                      </div>

                      <div>
                        <span>
                          Recovery ETA
                        </span>

                        <strong>
                          {formatEta(
                            assessment.recoveryEta,
                          )}
                        </strong>
                      </div>
                    </div>

                    <div className="damage-progress">
                      <div>
                        <span>
                          Repair progress
                        </span>

                        <strong>
                          {assessment.repairProgressPct}
                          %
                        </strong>
                      </div>

                      <div className="damage-progress-track">
                        <i
                          style={{
                            width:
                              `${assessment.repairProgressPct}%`,
                          }}
                        />
                      </div>
                    </div>

                    <div className="damage-owner">
                      <UserRound size={11} />

                      <span>
                        {assessment.owner}
                      </span>

                      {assessment.status === "REPORTED" && (
                        <AlertTriangle size={11} />
                      )}

                      {assessment.status === "RESTORED" && (
                        <CheckCircle2 size={11} />
                      )}
                    </div>

                    {!readOnly
                      && assessment.status
                        === "REPORTED" && (
                      <button
                        type="button"
                        className="damage-verify"
                        disabled={busy}
                        onClick={() =>
                          applyMutation(
                            verifyDamageAssessment(
                              incidentId,
                              assessment.id,
                            ),
                          )
                        }
                      >
                        <ClipboardCheck size={12} />

                        Verify assessment
                      </button>
                    )}

                    {!readOnly
                      && !restored && (
                      <>
                        <select
                          className="damage-select"
                          value={
                            assessment.severity
                          }
                          disabled={busy}
                          onChange={(event) =>
                            applyMutation(
                              updateDamageSeverity(
                                incidentId,
                                assessment.id,
                                event.target.value as DamageSeverity,
                              ),
                            )
                          }
                        >
                          <option value="MINOR">
                            Minor
                          </option>

                          <option value="MODERATE">
                            Moderate
                          </option>

                          <option value="MAJOR">
                            Major
                          </option>

                          <option value="DESTROYED">
                            Destroyed
                          </option>
                        </select>

                        {assessment.status
                          !== "REPORTED" && (
                          <form
                            className="damage-inline"
                            onSubmit={(event) => {
                              event.preventDefault();

                              const data =
                                new FormData(
                                  event.currentTarget,
                                );

                              const value =
                                Number(
                                  data.get(
                                    "progress",
                                  ),
                                );

                              if (
                                !Number.isFinite(
                                  value,
                                )
                                || value < 0
                                || value > 100
                              ) {
                                setError(
                                  "Repair progress must be between 0 and 100.",
                                );

                                return;
                              }

                              applyMutation(
                                updateDamageProgress(
                                  incidentId,
                                  assessment.id,
                                  value,
                                ),
                              );
                            }}
                          >
                            <input
                              name="progress"
                              type="number"
                              min={0}
                              max={100}
                              defaultValue={
                                assessment.repairProgressPct
                              }
                            />

                            <button
                              type="submit"
                              disabled={busy}
                            >
                              <Hammer size={11} />

                              Progress
                            </button>
                          </form>
                        )}

                        <form
                          className="damage-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const owner =
                              String(
                                data.get(
                                  "owner",
                                )
                                ?? "",
                              ).trim();

                            if (
                              owner === ""
                            ) {
                              return;
                            }

                            applyMutation(
                              updateDamageOwner(
                                incidentId,
                                assessment.id,
                                owner,
                              ),
                            );
                          }}
                        >
                          <input
                            name="owner"
                            maxLength={120}
                            defaultValue={
                              assessment.owner
                            }
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            Assign
                          </button>
                        </form>

                        <form
                          className="damage-inline"
                          onSubmit={(event) => {
                            event.preventDefault();

                            const data =
                              new FormData(
                                event.currentTarget,
                              );

                            const raw =
                              String(
                                data.get(
                                  "eta",
                                )
                                ?? "",
                              ).trim();

                            const eta =
                              raw === ""
                                ? null
                                : new Date(
                                  raw,
                                ).toISOString();

                            applyMutation(
                              updateDamageRecoveryEta(
                                incidentId,
                                assessment.id,
                                eta,
                              ),
                            );
                          }}
                        >
                          <input
                            name="eta"
                            type="datetime-local"
                          />

                          <button
                            type="submit"
                            disabled={busy}
                          >
                            ETA
                          </button>
                        </form>
                      </>
                    )}
                  </article>
                );
              },
            )
        }
      </div>
    </section>
  );
}
