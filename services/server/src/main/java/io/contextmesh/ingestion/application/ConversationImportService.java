package io.contextmesh.ingestion.application;

import io.contextmesh.conversation.application.ConversationIngestionService;
import io.contextmesh.conversation.application.IngestionStatus;
import io.contextmesh.conversation.domain.NormalizedConversation;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Synchronously orchestrates a batch; each ingest call retains its own transaction. */
@Service
public class ConversationImportService {
    private final ConversationIngestionService ingestionService;

    public ConversationImportService(ConversationIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public ConversationImportResult importConversations(UUID workspaceId,
                                                         List<NormalizedConversation> conversations) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        conversations = List.copyOf(Objects.requireNonNull(conversations, "conversations"));
        var results = new java.util.ArrayList<ConversationImportItemResult>(conversations.size());
        int imported = 0, duplicates = 0, conflicts = 0;
        for (int index = 0; index < conversations.size(); index++) {
            var conversation = conversations.get(index);
            var result = ingestionService.ingest(workspaceId, conversation);
            if (result.status() == IngestionStatus.IMPORTED) imported++;
            else if (result.status() == IngestionStatus.SKIPPED_DUPLICATE) duplicates++;
            else if (result.status() == IngestionStatus.CONFLICT) conflicts++;
            results.add(new ConversationImportItemResult(index, conversation.externalId(), result));
        }
        return new ConversationImportResult(conversations.size(), imported, duplicates, conflicts, results);
    }
}
