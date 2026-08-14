ALTER TABLE conversations ALTER COLUMN source_fingerprint DROP NOT NULL;
ALTER TABLE conversations ALTER COLUMN imported_at DROP NOT NULL;

ALTER TABLE conversations ADD CONSTRAINT conversations_source_lifecycle_check CHECK (
    (source_type = 'IMPORTED_CONVERSATION' AND source_fingerprint IS NOT NULL AND imported_at IS NOT NULL)
    OR
    (source_type = 'NATIVE_CONVERSATION' AND source_fingerprint IS NULL AND imported_at IS NULL
        AND source_provider IS NULL AND external_id IS NULL)
);

CREATE UNIQUE INDEX messages_conversation_external_id_unique
    ON messages (conversation_id, external_id)
    WHERE external_id IS NOT NULL;
