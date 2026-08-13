package io.contextmesh.ingestion.adapter.http;

import io.contextmesh.ingestion.adapter.genericjson.GenericJsonConversationImporter;
import io.contextmesh.ingestion.application.ConversationImportResult;
import io.contextmesh.ingestion.application.ConversationImportService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/imports/conversations")
public final class ConversationImportController {
    private final GenericJsonConversationImporter importer;
    private final ConversationImportService importService;

    public ConversationImportController(GenericJsonConversationImporter importer,
                                        ConversationImportService importService) {
        this.importer = importer;
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversationImportResult importGenericJson(@PathVariable UUID workspaceId,
                                                       @RequestBody String body) {
        // Normalize the complete input before starting any per-conversation transaction.
        return importService.importConversations(workspaceId, importer.importConversations(body));
    }
}
