ALTER TABLE incident_timeline_events
    DROP CONSTRAINT ck_incident_timeline_type;

ALTER TABLE incident_timeline_events
    ADD CONSTRAINT ck_incident_timeline_type
        CHECK (
            event_type IN (
                'INCIDENT_OPENED',
                'STATUS_CHANGED',
                'SEVERITY_CHANGED',
                'OWNER_CHANGED',
                'NOTE_ADDED',

                'ACTION_CREATED',
                'ACTION_STATUS_CHANGED',
                'ACTION_PRIORITY_CHANGED',
                'ACTION_OWNER_CHANGED',
                'ACTION_DUE_CHANGED',
                'ACTION_DELETED',

                'RESOURCE_REQUESTED',
                'RESOURCE_ALLOCATION_CHANGED',
                'RESOURCE_PRIORITY_CHANGED',
                'RESOURCE_OWNER_CHANGED',
                'RESOURCE_CANCELLED',
                'RESOURCE_DELETED',

                'EVACUATION_ZONE_CREATED',
                'EVACUATION_PROGRESS_CHANGED',
                'EVACUATION_STATUS_CHANGED',
                'EVACUATION_OWNER_CHANGED',
                'EVACUATION_ZONE_DELETED',

                'SHELTER_CREATED',
                'SHELTER_OCCUPANCY_CHANGED',
                'SHELTER_STATUS_CHANGED',
                'SHELTER_OWNER_CHANGED',
                'SHELTER_DELETED',

                'LIFELINE_ASSET_CREATED',
                'LIFELINE_STATUS_CHANGED',
                'LIFELINE_IMPACT_CHANGED',
                'LIFELINE_OWNER_CHANGED',
                'LIFELINE_ETA_CHANGED',
                'LIFELINE_ASSET_DELETED',

                'DAMAGE_ASSESSMENT_CREATED',
                'DAMAGE_VERIFIED',
                'DAMAGE_SEVERITY_CHANGED',
                'DAMAGE_PROGRESS_CHANGED',
                'DAMAGE_OWNER_CHANGED',
                'DAMAGE_ETA_CHANGED',
                'DAMAGE_ASSESSMENT_DELETED',

                'COMMAND_SNAPSHOT_CREATED',
                'SITREP_CREATED',
                'AFTER_ACTION_REVIEW_CREATED',

                'IMPROVEMENT_ACTION_CREATED',
                'IMPROVEMENT_STATUS_CHANGED',
                'IMPROVEMENT_PRIORITY_CHANGED',
                'IMPROVEMENT_OWNER_CHANGED',
                'IMPROVEMENT_DUE_CHANGED',
                'IMPROVEMENT_ACTION_DELETED',

                'PREPAREDNESS_EXERCISE_CREATED',
                'PREPAREDNESS_EXERCISE_STATUS_CHANGED',
                'PREPAREDNESS_EXERCISE_OWNER_CHANGED',
                'PREPAREDNESS_EXERCISE_SCHEDULE_CHANGED',
                'PREPAREDNESS_EXERCISE_ASSESSED',
                'PREPAREDNESS_EXERCISE_DELETED',

                'PREPAREDNESS_CORRECTIVE_ACTION_CREATED',
                'PREPAREDNESS_CORRECTIVE_STATUS_CHANGED',
                'PREPAREDNESS_CORRECTIVE_OWNER_CHANGED',
                'PREPAREDNESS_CORRECTIVE_DUE_CHANGED',
                'PREPAREDNESS_CORRECTIVE_ACTION_DELETED',

                'RESILIENCE_CERTIFICATION_CREATED',

                'RESILIENCE_EVIDENCE_PACKAGE_CREATED',

                'RESILIENCE_AUDIT_RECEIPT_CREATED'
            )
        );

CREATE TABLE incident_resilience_audit_receipts (
    id UUID PRIMARY KEY,

    incident_id UUID NOT NULL
        REFERENCES incident_cases(id)
        ON DELETE CASCADE,

    receipt_number INTEGER NOT NULL,

    evidence_package_id UUID NOT NULL
        REFERENCES incident_resilience_evidence_packages(id)
        ON DELETE CASCADE,

    evidence_package_number INTEGER NOT NULL,

    package_manifest_version VARCHAR(80) NOT NULL,

    package_manifest_hash VARCHAR(64) NOT NULL,

    previous_receipt_hash VARCHAR(64),

    ledger_version VARCHAR(80) NOT NULL,

    ledger_hash VARCHAR(64) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT NOW(),

    CONSTRAINT uq_resilience_audit_receipt_number
        UNIQUE (
            incident_id,
            receipt_number
        ),

    CONSTRAINT uq_resilience_audit_evidence_package
        UNIQUE (
            evidence_package_id
        ),

    CONSTRAINT ck_resilience_audit_receipt_number
        CHECK (
            receipt_number >= 1
        ),

    CONSTRAINT ck_resilience_audit_package_number
        CHECK (
            evidence_package_number >= 1
        ),

    CONSTRAINT ck_resilience_audit_package_hash
        CHECK (
            package_manifest_hash
            ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_resilience_audit_previous_hash
        CHECK (
            previous_receipt_hash IS NULL
            OR previous_receipt_hash
               ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT ck_resilience_audit_ledger_hash
        CHECK (
            ledger_hash
            ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_resilience_audit_incident
    ON incident_resilience_audit_receipts (
        incident_id,
        receipt_number DESC
    );

CREATE INDEX idx_resilience_audit_package
    ON incident_resilience_audit_receipts (
        evidence_package_id
    );

CREATE INDEX idx_resilience_audit_hash
    ON incident_resilience_audit_receipts (
        ledger_hash
    );
