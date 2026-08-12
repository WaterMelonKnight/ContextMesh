CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    display_name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE workspaces (
    id uuid PRIMARY KEY,
    owner_user_id uuid NOT NULL REFERENCES users (id),
    name varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX workspaces_owner_user_id_idx ON workspaces (owner_user_id);

