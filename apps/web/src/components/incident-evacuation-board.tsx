"use client";

import {
  Building2,
  CheckCircle2,
  DoorOpen,
  House,
  MapPinned,
  Plus,
  RefreshCw,
  ShieldAlert,
  Trash2,
  UserRound,
  Users,
} from "lucide-react";

import {
  FormEvent,
  useEffect,
  useState,
} from "react";

import {
  createEvacuationZone,
  createIncidentShelter,
  deleteEvacuationZone,
  deleteIncidentShelter,
  fetchEvacuationOperationsBoard,
  updateEvacuationProgress,
  updateEvacuationZoneOwner,
  updateEvacuationZoneStatus,
  updateShelterOccupancy,
  updateShelterOwner,
  updateShelterStatus,
} from "@/lib/api";

import type {
  IncidentActionPriority,
} from "@/types/incident-actions";

import type {
  CreateEvacuationZoneRequest,
  CreateShelterRequest,
  EvacuationOperationsBoardResponse,
  EvacuationZoneStatus,
  ShelterStatus,
} from "@/types/evacuation-operations";

import type {
  IncidentStatus,
} from "@/types/incidents";

type IncidentEvacuationBoardProps = {
  incidentId: string;
  incidentStatus: IncidentStatus;
  onTimelineChanged: () => void;
};

const initialZone:
  CreateEvacuationZoneRequest = {
    name: "",
    description: "",
    priority: "HIGH",
    populationAtRisk: 100,
    evacuatedPopulation: 0,
    owner: "Unassigned",
  };

const initialShelter:
  CreateShelterRequest = {
    name: "",
    address: "",
    capacity: 100,
    occupancy: 0,
    owner: "Unassigned",
  };

function zoneStatusClass(
  status: EvacuationZoneStatus,
): string {
  return (
    `evac-status evac-status--${status.toLowerCase()}`
  );
}

function shelterStatusClass(
  status: ShelterStatus,
): string {
  return (
    `shelter-status shelter-status--${status.toLowerCase()}`
  );
}

export function IncidentEvacuationBoard({
  incidentId,
  incidentStatus,
  onTimelineChanged,
}: IncidentEvacuationBoardProps) {
  const [
    board,
    setBoard,
  ] =
    useState<
      EvacuationOperationsBoardResponse | null
    >(null);

  const [
    zoneDraft,
    setZoneDraft,
  ] =
    useState(
      initialZone,
    );

  const [
    shelterDraft,
    setShelterDraft,
  ] =
    useState(
      initialShelter,
    );

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

    void fetchEvacuationOperationsBoard(
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
              : "Unable to load evacuation operations.",
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
      Promise<EvacuationOperationsBoardResponse>,
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
              : "Unable to update evacuation operations.",
          );
        },
      )
      .finally(() => {
        setBusy(false);
      });
  };

  const handleZoneCreate = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      zoneDraft.name.trim()
      === ""
    ) {
      return;
    }

    applyMutation(
      createEvacuationZone(
        incidentId,
        {
          ...zoneDraft,
          name:
            zoneDraft.name.trim(),
          description:
            zoneDraft.description.trim(),
          owner:
            zoneDraft.owner.trim()
            || "Unassigned",
        },
      ),
    );

    setZoneDraft(
      initialZone,
    );
  };

  const handleShelterCreate = (
    event:
      FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (
      shelterDraft.name.trim()
      === ""
    ) {
      return;
    }

    applyMutation(
      createIncidentShelter(
        incidentId,
        {
          ...shelterDraft,
          name:
            shelterDraft.name.trim(),
          address:
            shelterDraft.address.trim(),
          owner:
            shelterDraft.owner.trim()
            || "Unassigned",
        },
      ),
    );

    setShelterDraft(
      initialShelter,
    );
  };

  const readOnly =
    incidentStatus === "CLOSED";

  return (
    <section className="evacuation-board">
      <div className="evacuation-heading">
        <div>
          <span className="panel-kicker">
            EVACUATION OPERATIONS
          </span>

          <strong>
            Population & shelter coordination
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

            void fetchEvacuationOperationsBoard(
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
                      : "Unable to refresh evacuation operations.",
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

      <div className="evacuation-summary">
        <div>
          <span>
            At risk
          </span>

          <strong>
            {board?.populationAtRisk
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Evacuated
          </span>

          <strong>
            {board?.evacuatedPopulation
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Remaining
          </span>

          <strong>
            {board?.remainingToEvacuate
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Shelter capacity
          </span>

          <strong>
            {board?.shelterCapacity
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Occupancy
          </span>

          <strong>
            {board?.shelterOccupancy
              ?? 0}
          </strong>
        </div>

        <div>
          <span>
            Free capacity
          </span>

          <strong>
            {board
              ?.remainingShelterCapacity
              ?? 0}
          </strong>
        </div>
      </div>

      {error !== null && (
        <div className="evacuation-error">
          {error}
        </div>
      )}

      {!readOnly && (
        <div className="evacuation-create-grid">
          <form
            className="evacuation-form"
            onSubmit={
              handleZoneCreate
            }
          >
            <div className="evacuation-form-heading">
              <MapPinned size={14} />

              <strong>
                New evacuation zone
              </strong>
            </div>

            <label>
              <span>
                Zone
              </span>

              <input
                required
                maxLength={300}
                value={zoneDraft.name}
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    name:
                      event.target.value,
                  })
                }
              />
            </label>

            <label>
              <span>
                Priority
              </span>

              <select
                value={
                  zoneDraft.priority
                }
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    priority:
                      event.target.value as IncidentActionPriority,
                  })
                }
              >
                <option value="LOW">
                  Low
                </option>
                <option value="MEDIUM">
                  Medium
                </option>
                <option value="HIGH">
                  High
                </option>
                <option value="CRITICAL">
                  Critical
                </option>
              </select>
            </label>

            <label>
              <span>
                Population at risk
              </span>

              <input
                type="number"
                min={1}
                max={100000000}
                value={
                  zoneDraft
                    .populationAtRisk
                }
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    populationAtRisk:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label>
              <span>
                Already evacuated
              </span>

              <input
                type="number"
                min={0}
                max={
                  zoneDraft
                    .populationAtRisk
                }
                value={
                  zoneDraft
                    .evacuatedPopulation
                }
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    evacuatedPopulation:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label>
              <span>
                Owner
              </span>

              <input
                maxLength={120}
                value={
                  zoneDraft.owner
                }
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    owner:
                      event.target.value,
                  })
                }
              />
            </label>

            <label>
              <span>
                Description
              </span>

              <textarea
                maxLength={2000}
                value={
                  zoneDraft.description
                }
                onChange={(event) =>
                  setZoneDraft({
                    ...zoneDraft,
                    description:
                      event.target.value,
                  })
                }
              />
            </label>

            <button
              type="submit"
              disabled={busy}
            >
              <Plus size={13} />

              Add zone
            </button>
          </form>

          <form
            className="evacuation-form"
            onSubmit={
              handleShelterCreate
            }
          >
            <div className="evacuation-form-heading">
              <House size={14} />

              <strong>
                New shelter
              </strong>
            </div>

            <label>
              <span>
                Shelter
              </span>

              <input
                required
                maxLength={300}
                value={
                  shelterDraft.name
                }
                onChange={(event) =>
                  setShelterDraft({
                    ...shelterDraft,
                    name:
                      event.target.value,
                  })
                }
              />
            </label>

            <label>
              <span>
                Address
              </span>

              <input
                maxLength={500}
                value={
                  shelterDraft.address
                }
                onChange={(event) =>
                  setShelterDraft({
                    ...shelterDraft,
                    address:
                      event.target.value,
                  })
                }
              />
            </label>

            <label>
              <span>
                Capacity
              </span>

              <input
                type="number"
                min={1}
                max={10000000}
                value={
                  shelterDraft.capacity
                }
                onChange={(event) =>
                  setShelterDraft({
                    ...shelterDraft,
                    capacity:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label>
              <span>
                Occupancy
              </span>

              <input
                type="number"
                min={0}
                max={
                  shelterDraft.capacity
                }
                value={
                  shelterDraft.occupancy
                }
                onChange={(event) =>
                  setShelterDraft({
                    ...shelterDraft,
                    occupancy:
                      Number(
                        event.target.value,
                      ),
                  })
                }
              />
            </label>

            <label>
              <span>
                Owner
              </span>

              <input
                maxLength={120}
                value={
                  shelterDraft.owner
                }
                onChange={(event) =>
                  setShelterDraft({
                    ...shelterDraft,
                    owner:
                      event.target.value,
                  })
                }
              />
            </label>

            <button
              type="submit"
              disabled={busy}
            >
              <Plus size={13} />

              Add shelter
            </button>
          </form>
        </div>
      )}

      {readOnly && (
        <div className="evacuation-readonly">
          This incident is closed.
          Evacuation operations are read-only.
        </div>
      )}

      <div className="evacuation-section-heading">
        <Users size={14} />

        <strong>
          Evacuation zones
        </strong>

        <span>
          {board?.activeZoneCount
            ?? 0}
          {" active · "}
          {board?.clearedZoneCount
            ?? 0}
          {" cleared"}
        </span>
      </div>

      <div className="evacuation-zone-list">
        {loading
          && board === null
          ? (
            <div className="evacuation-empty">
              Loading zones…
            </div>
          )
          : board === null
            || board.zones.length
              === 0
            ? (
              <div className="evacuation-empty">
                No evacuation zones.
              </div>
            )
            : board.zones.map(
              (zone) => (
                <article
                  key={zone.id}
                  className="evacuation-zone-card"
                >
                  <div className="evacuation-card-top">
                    <div>
                      <span
                        className={
                          zoneStatusClass(
                            zone.status,
                          )
                        }
                      >
                        {zone.status}
                      </span>

                      <span className="evacuation-priority">
                        {zone.priority}
                      </span>
                    </div>

                    {!readOnly && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() =>
                          applyMutation(
                            deleteEvacuationZone(
                              incidentId,
                              zone.id,
                            ),
                          )
                        }
                      >
                        <Trash2
                          size={13}
                        />
                      </button>
                    )}
                  </div>

                  <strong>
                    {zone.name}
                  </strong>

                  {zone.description
                    !== "" && (
                    <p>
                      {zone.description}
                    </p>
                  )}

                  <div className="evacuation-progress">
                    <span>
                      {zone.evacuatedPopulation}
                      {" / "}
                      {zone.populationAtRisk}
                    </span>

                    <div>
                      <i
                        style={{
                          width:
                            `${
                              zone.populationAtRisk
                                === 0
                                ? 0
                                : (
                                  zone.evacuatedPopulation
                                  / zone.populationAtRisk
                                )
                                * 100
                            }%`,
                        }}
                      />
                    </div>
                  </div>

                  <div className="evacuation-meta">
                    <UserRound
                      size={11}
                    />

                    <span>
                      {zone.owner}
                    </span>
                  </div>

                  {!readOnly
                    && zone.status
                      !== "CANCELLED" && (
                    <>
                      <form
                        className="evacuation-inline"
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
                            || value
                              > zone.populationAtRisk
                          ) {
                            setError(
                              "Evacuated population must be between 0 and population at risk.",
                            );

                            return;
                          }

                          applyMutation(
                            updateEvacuationProgress(
                              incidentId,
                              zone.id,
                              value,
                            ),
                          );
                        }}
                      >
                        <input
                          name="progress"
                          type="number"
                          min={0}
                          max={
                            zone.populationAtRisk
                          }
                          defaultValue={
                            zone.evacuatedPopulation
                          }
                        />

                        <button
                          type="submit"
                          disabled={busy}
                        >
                          Update
                        </button>
                      </form>

                      <select
                        className="evacuation-status-select"
                        disabled={busy}
                        value={
                          zone.status
                        }
                        onChange={(event) =>
                          applyMutation(
                            updateEvacuationZoneStatus(
                              incidentId,
                              zone.id,
                              event.target.value as EvacuationZoneStatus,
                            ),
                          )
                        }
                      >
                        <option value="MONITORING">
                          Monitoring
                        </option>

                        <option value="EVACUATING">
                          Evacuating
                        </option>

                        <option value="CLEARED">
                          Cleared
                        </option>

                        <option value="CANCELLED">
                          Cancelled
                        </option>
                      </select>

                      <form
                        className="evacuation-inline"
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
                            updateEvacuationZoneOwner(
                              incidentId,
                              zone.id,
                              owner,
                            ),
                          );
                        }}
                      >
                        <input
                          name="owner"
                          maxLength={120}
                          defaultValue={
                            zone.owner
                          }
                        />

                        <button
                          type="submit"
                          disabled={busy}
                        >
                          Assign
                        </button>
                      </form>
                    </>
                  )}
                </article>
              ),
            )
        }
      </div>

      <div className="evacuation-section-heading">
        <Building2 size={14} />

        <strong>
          Shelters
        </strong>

        <span>
          {board?.openShelterCount
            ?? 0}
          {" open · "}
          {board?.fullShelterCount
            ?? 0}
          {" full"}
        </span>
      </div>

      <div className="shelter-list">
        {board === null
          || board.shelters.length
            === 0
          ? (
            <div className="evacuation-empty">
              No shelters.
            </div>
          )
          : board.shelters.map(
            (shelter) => (
              <article
                className="shelter-card"
                key={shelter.id}
              >
                <div className="evacuation-card-top">
                  <span
                    className={
                      shelterStatusClass(
                        shelter.status,
                      )
                    }
                  >
                    {shelter.status}
                  </span>

                  {!readOnly && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        applyMutation(
                          deleteIncidentShelter(
                            incidentId,
                            shelter.id,
                          ),
                        )
                      }
                    >
                      <Trash2
                        size={13}
                      />
                    </button>
                  )}
                </div>

                <strong>
                  {shelter.name}
                </strong>

                {shelter.address
                  !== "" && (
                  <p>
                    {shelter.address}
                  </p>
                )}

                <div className="shelter-capacity">
                  <div>
                    <Users
                      size={12}
                    />

                    <span>
                      {shelter.occupancy}
                      {" / "}
                      {shelter.capacity}
                    </span>
                  </div>

                  <strong>
                    {Math.max(
                      0,
                      shelter.capacity
                      - shelter.occupancy,
                    )}
                    {" free"}
                  </strong>
                </div>

                <div className="evacuation-meta">
                  {shelter.status
                    === "FULL"
                    ? (
                      <ShieldAlert
                        size={11}
                      />
                    )
                    : shelter.status
                      === "CLOSED"
                      ? (
                        <DoorOpen
                          size={11}
                        />
                      )
                      : (
                        <CheckCircle2
                          size={11}
                        />
                      )
                  }

                  <span>
                    {shelter.owner}
                  </span>
                </div>

                {!readOnly
                  && shelter.status
                    !== "CLOSED" && (
                  <form
                    className="evacuation-inline"
                    onSubmit={(event) => {
                      event.preventDefault();

                      const data =
                        new FormData(
                          event.currentTarget,
                        );

                      const occupancy =
                        Number(
                          data.get(
                            "occupancy",
                          ),
                        );

                      if (
                        !Number.isFinite(
                          occupancy,
                        )
                        || occupancy < 0
                        || occupancy
                          > shelter.capacity
                      ) {
                        setError(
                          "Shelter occupancy must be between 0 and shelter capacity.",
                        );

                        return;
                      }

                      applyMutation(
                        updateShelterOccupancy(
                          incidentId,
                          shelter.id,
                          occupancy,
                        ),
                      );
                    }}
                  >
                    <input
                      name="occupancy"
                      type="number"
                      min={0}
                      max={
                        shelter.capacity
                      }
                      defaultValue={
                        shelter.occupancy
                      }
                    />

                    <button
                      type="submit"
                      disabled={busy}
                    >
                      Update
                    </button>
                  </form>
                )}

                {!readOnly && (
                  <>
                    <select
                      className="evacuation-status-select"
                      disabled={busy}
                      value={
                        shelter.status
                      }
                      onChange={(event) =>
                        applyMutation(
                          updateShelterStatus(
                            incidentId,
                            shelter.id,
                            event.target.value as ShelterStatus,
                          ),
                        )
                      }
                    >
                      <option value="OPEN">
                        Open
                      </option>

                      <option value="FULL">
                        Full
                      </option>

                      <option value="CLOSED">
                        Closed
                      </option>
                    </select>

                    <form
                      className="evacuation-inline"
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
                          updateShelterOwner(
                            incidentId,
                            shelter.id,
                            owner,
                          ),
                        );
                      }}
                    >
                      <input
                        name="owner"
                        maxLength={120}
                        defaultValue={
                          shelter.owner
                        }
                      />

                      <button
                        type="submit"
                        disabled={busy}
                      >
                        Assign
                      </button>
                    </form>
                  </>
                )}
              </article>
            ),
          )
        }
      </div>
    </section>
  );
}
