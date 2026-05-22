-- Lookup table for complaint status (3 fixed values seeded in V2).
CREATE TABLE t_status (
    status_id   INTEGER     PRIMARY KEY,
    status_name VARCHAR(32) NOT NULL UNIQUE
);

-- Admin user table (passworded auth in Batch 3).
CREATE TABLE t_admin (
    admin_id      BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Resident user table. flat_no is unique and doubles as the login identifier.
CREATE TABLE t_resident (
    resident_id   BIGSERIAL    PRIMARY KEY,
    resident_name VARCHAR(120) NOT NULL,
    flat_no       VARCHAR(16)  NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Reusable directory of professionals (workers) admins can assign to complaints.
CREATE TABLE t_professional (
    professional_id BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    phone           VARCHAR(32)  NOT NULL,
    category        VARCHAR(24)  NOT NULL
);

CREATE INDEX idx_professional_category ON t_professional(category);

-- Core transaction table: one row per raised complaint.
CREATE TABLE t_complaint (
    complaint_id    BIGSERIAL    PRIMARY KEY,
    resident_id     BIGINT       NOT NULL REFERENCES t_resident(resident_id),
    category        VARCHAR(24)  NOT NULL,
    description     TEXT         NOT NULL,
    status_id       INTEGER      NOT NULL REFERENCES t_status(status_id),
    professional_id BIGINT       REFERENCES t_professional(professional_id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at     TIMESTAMP WITH TIME ZONE,
    completed_at    TIMESTAMP WITH TIME ZONE,

    -- Status invariants:
    --   1 = Assignment Pending: no professional, no assignment/completion timestamps
    --   2 = Pending Work:       professional set, assigned_at set, not completed
    --   3 = Complete:           professional set, assigned_at set, completed_at set
    CONSTRAINT chk_complaint_status_invariant CHECK (
        (status_id = 1 AND professional_id IS NULL     AND assigned_at IS NULL     AND completed_at IS NULL) OR
        (status_id = 2 AND professional_id IS NOT NULL AND assigned_at IS NOT NULL AND completed_at IS NULL) OR
        (status_id = 3 AND professional_id IS NOT NULL AND assigned_at IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_complaint_resident_id     ON t_complaint(resident_id);
CREATE INDEX idx_complaint_status_id       ON t_complaint(status_id);
CREATE INDEX idx_complaint_created_at_desc ON t_complaint(created_at DESC);
