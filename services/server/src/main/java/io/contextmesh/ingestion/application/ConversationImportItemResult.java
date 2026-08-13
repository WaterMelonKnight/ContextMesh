package io.contextmesh.ingestion.application;

import io.contextmesh.conversation.application.IngestionResult;

public record ConversationImportItemResult(int index, String externalId, IngestionResult ingestionResult) {
}
