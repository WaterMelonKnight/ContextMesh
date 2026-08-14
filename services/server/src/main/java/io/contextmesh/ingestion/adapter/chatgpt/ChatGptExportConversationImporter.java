package io.contextmesh.ingestion.adapter.chatgpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.ingestion.application.ConversationImporter;
import io.contextmesh.ingestion.application.ImportSource;
import java.util.List;
import org.springframework.stereotype.Component;

/** Normalizes the official ChatGPT conversations.json representation. */
@Component
public final class ChatGptExportConversationImporter implements ConversationImporter<String> {
    private final ChatGptExportParser parser;

    public ChatGptExportConversationImporter(ObjectMapper objectMapper) {
        this.parser = new ChatGptExportParser(objectMapper);
    }

    @Override public ImportSource source() { return ImportSource.CHATGPT_OFFICIAL_EXPORT; }
    @Override public List<NormalizedConversation> importConversations(String input) {
        return parser.parse(input);
    }
}
