-- Submissions reference the immutable draft revision instead of copying language/source code.
ALTER TABLE assessment_question_submissions ADD COLUMN draft_revision_id VARCHAR(255);

UPDATE assessment_question_submissions s
SET draft_revision_id = d.id
FROM assessment_question_draft_revisions d
WHERE d.workspace_id = s.workspace_id
  AND d.revision = s.draft_revision;

ALTER TABLE assessment_question_submissions ALTER COLUMN draft_revision_id SET NOT NULL;

ALTER TABLE assessment_question_submissions
    ADD CONSTRAINT fk_submission_draft_revision
        FOREIGN KEY (draft_revision_id) REFERENCES assessment_question_draft_revisions (id);

CREATE UNIQUE INDEX uk_submission_draft_revision ON assessment_question_submissions (draft_revision_id);

ALTER TABLE assessment_question_submissions
    DROP COLUMN language,
    DROP COLUMN source_code,
    DROP COLUMN draft_revision;

-- Declared on the entity but missing from earlier migrations.
CREATE UNIQUE INDEX idx_submission_workspace_submission_number
    ON assessment_question_submissions (workspace_id, submission_number);
