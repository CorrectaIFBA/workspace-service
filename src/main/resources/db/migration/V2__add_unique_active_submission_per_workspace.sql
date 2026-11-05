CREATE UNIQUE INDEX uk_submission_active_workspace
    ON assessment_question_submissions (workspace_id)
    WHERE status = 'ACTIVE';
