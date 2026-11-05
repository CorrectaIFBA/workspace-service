-- Schema of every entity.

CREATE TABLE assessment_question_workspaces (
    id                 VARCHAR(255)                NOT NULL,
    assessment_id      VARCHAR(255)                NOT NULL,
    question_id        VARCHAR(255)                NOT NULL,
    student_profile_id VARCHAR(255)                NOT NULL,
    entity_version     BIGINT,
    current_revision   INTEGER                     NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assessment_question_workspaces_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX idx_workspace_student_assessment_question
    ON assessment_question_workspaces (student_profile_id, assessment_id, question_id);
CREATE INDEX idx_workspace_assessment_question ON assessment_question_workspaces (assessment_id, question_id);
CREATE INDEX idx_workspace_student_profile ON assessment_question_workspaces (student_profile_id);

CREATE TABLE assessment_question_draft_revisions (
    id           VARCHAR(255)                NOT NULL,
    workspace_id VARCHAR(255)                NOT NULL,
    language     VARCHAR(255)                NOT NULL,
    source_code  TEXT                        NOT NULL,
    revision     INTEGER                     NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assessment_question_draft_revisions_pkey PRIMARY KEY (id),
    CONSTRAINT fk_draft_revision_workspace FOREIGN KEY (workspace_id) REFERENCES assessment_question_workspaces (id)
);

CREATE UNIQUE INDEX idx_draft_revision_workspace_revision
    ON assessment_question_draft_revisions (workspace_id, revision);
CREATE INDEX idx_draft_revision_workspace_created_at
    ON assessment_question_draft_revisions (workspace_id, created_at);

CREATE TABLE assessment_question_submissions (
    id                VARCHAR(255)                NOT NULL,
    workspace_id      VARCHAR(255)                NOT NULL,
    submission_number INTEGER                     NOT NULL,
    status            VARCHAR(255)                NOT NULL,
    language          VARCHAR(255)                NOT NULL,
    source_code       TEXT                        NOT NULL,
    draft_revision    INTEGER                     NOT NULL,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assessment_question_submissions_pkey PRIMARY KEY (id),
    CONSTRAINT assessment_question_submissions_status_check CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT fk_submission_workspace FOREIGN KEY (workspace_id) REFERENCES assessment_question_workspaces (id)
);

CREATE INDEX idx_submission_workspace_created_at ON assessment_question_submissions (workspace_id, created_at);
