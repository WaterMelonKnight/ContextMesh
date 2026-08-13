package io.contextmesh.ingestion.adapter.genericjson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.ingestion.application.ConversationImporter;
import io.contextmesh.ingestion.application.ImportSource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class GenericJsonConversationImporter implements ConversationImporter<String> {
    private final GenericConversationJsonParser parser;

    public GenericJsonConversationImporter(ObjectMapper objectMapper) {
        parser = new GenericConversationJsonParser(objectMapper);
    }

    @Override public ImportSource source() { return ImportSource.GENERIC_JSON; }
    @Override public List<NormalizedConversation> importConversations(String input) {
        return parser.parseAll(input);
    }
}
