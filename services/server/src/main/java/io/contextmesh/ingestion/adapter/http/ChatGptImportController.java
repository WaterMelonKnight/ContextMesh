package io.contextmesh.ingestion.adapter.http;

import io.contextmesh.ingestion.adapter.chatgpt.ChatGptExportConversationImporter;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/imports/chatgpt")
public final class ChatGptImportController {
    private final ChatGptExportConversationImporter importer;
    private final ConversationImportService importService;

    public ChatGptImportController(ChatGptExportConversationImporter importer,
                                   ConversationImportService importService) {
        this.importer = importer;
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversationImportResult importExport(@PathVariable UUID workspaceId, @RequestBody String body) {
        return importService.importConversations(workspaceId, importer.importConversations(body));
    }
}
