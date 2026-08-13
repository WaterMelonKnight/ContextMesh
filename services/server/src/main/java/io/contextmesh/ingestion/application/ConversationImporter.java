package io.contextmesh.ingestion.application;

import io.contextmesh.conversation.domain.NormalizedConversation;
import java.util.List;

/** A source adapter that only parses, validates, and normalizes input. */
public interface ConversationImporter<I> {
    ImportSource source();
    List<NormalizedConversation> importConversations(I input);
}
