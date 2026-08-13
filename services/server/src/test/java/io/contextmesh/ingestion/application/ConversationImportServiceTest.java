package io.contextmesh.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import io.contextmesh.conversation.application.ConversationIngestionService;
import io.contextmesh.conversation.application.IngestionResult;
import io.contextmesh.conversation.application.IngestionStatus;
import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.NormalizedConversation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationImportServiceTest {
    @Test
    void summarizesIndependentPerConversationIngestionResults() {
        var ingestion = mock(ConversationIngestionService.class);
        var service = new ConversationImportService(ingestion);
        var workspaceId = UUID.randomUUID();
        var first = conversation("one");
        var duplicate = conversation("one");
        var conflict = conversation("one");
        var second = conversation("two");
        when(ingestion.ingest(any(), any())).thenReturn(result(IngestionStatus.IMPORTED),
                result(IngestionStatus.SKIPPED_DUPLICATE), result(IngestionStatus.CONFLICT),
                result(IngestionStatus.IMPORTED));

        var summary = service.importConversations(workspaceId, List.of(first, duplicate, conflict, second));

        assertThat(summary.totalReceived()).isEqualTo(4);
        assertThat(summary.importedCount()).isEqualTo(2);
        assertThat(summary.skippedDuplicateCount()).isOne();
        assertThat(summary.conflictCount()).isOne();
        assertThat(summary.results()).extracting(ConversationImportItemResult::index)
                .containsExactly(0, 1, 2, 3);
        assertThat(summary.results()).extracting(item -> item.ingestionResult().status())
                .containsExactly(IngestionStatus.IMPORTED, IngestionStatus.SKIPPED_DUPLICATE,
                        IngestionStatus.CONFLICT, IngestionStatus.IMPORTED);
    }

    private static NormalizedConversation conversation(String externalId) {
        return new NormalizedConversation(externalId, null, ConversationSourceType.IMPORTED_CONVERSATION,
                null, null, null, List.of(), Map.of());
    }

    private static IngestionResult result(IngestionStatus status) {
        return new IngestionResult(status, UUID.randomUUID(), 0, "fingerprint");
    }
}
