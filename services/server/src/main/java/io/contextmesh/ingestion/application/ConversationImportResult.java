package io.contextmesh.ingestion.application;

import java.util.List;

public record ConversationImportResult(int totalReceived, int importedCount,
                                       int skippedDuplicateCount, int conflictCount,
                                       List<ConversationImportItemResult> results) {
    public ConversationImportResult {
        results = List.copyOf(results);
    }
}
