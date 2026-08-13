CREATE TABLE conversations (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id),
    source_type varchar(50) NOT NULL,
    source_provider varchar(200),
    external_id varchar(500),
    title text,
    source_created_at timestamptz,
    source_updated_at timestamptz,
    source_fingerprint char(64) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    imported_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT conversations_workspace_id_id_unique UNIQUE (workspace_id, id)
);

CREATE UNIQUE INDEX conversations_external_source_identity_unique
    ON conversations (workspace_id, source_type, source_provider, external_id) NULLS NOT DISTINCT
    WHERE external_id IS NOT NULL;
CREATE UNIQUE INDEX conversations_fingerprint_identity_unique
    ON conversations (workspace_id, source_fingerprint)
    WHERE external_id IS NULL;
CREATE INDEX conversations_workspace_imported_at_idx ON conversations (workspace_id, imported_at DESC);

CREATE TABLE messages (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES workspaces (id),
    conversation_id uuid NOT NULL,
    external_id varchar(500),
    sequence_no integer NOT NULL CHECK (sequence_no >= 0),
    role varchar(30) NOT NULL,
    source_created_at timestamptz,
    parent_external_id varchar(500),
    content jsonb NOT NULL,
    generation_provider varchar(200),
    generation_model varchar(300),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL,
    CONSTRAINT messages_conversation_sequence_unique UNIQUE (conversation_id, sequence_no),
    CONSTRAINT messages_workspace_conversation_fk FOREIGN KEY (workspace_id, conversation_id)
        REFERENCES conversations (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX messages_workspace_conversation_sequence_idx
    ON messages (workspace_id, conversation_id, sequence_no);
