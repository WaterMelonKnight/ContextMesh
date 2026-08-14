ALTER TABLE messages
    ADD CONSTRAINT messages_workspace_conversation_id_unique
    UNIQUE (workspace_id, conversation_id, id);

CREATE TABLE conversation_continuations (
    target_conversation_id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL,
    source_conversation_id uuid NOT NULL,
    through_message_id uuid,
    created_at timestamptz NOT NULL,
    CONSTRAINT continuation_target_fk
        FOREIGN KEY (workspace_id, target_conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT continuation_source_fk
        FOREIGN KEY (workspace_id, source_conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT continuation_cutoff_fk
        FOREIGN KEY (workspace_id, source_conversation_id, through_message_id)
        REFERENCES messages (workspace_id, conversation_id, id)
);

CREATE INDEX conversation_continuations_source_idx
    ON conversation_continuations (workspace_id, source_conversation_id);
