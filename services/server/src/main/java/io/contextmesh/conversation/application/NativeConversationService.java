package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NativeConversationService {
    private final NativeConversationPersistencePort persistence;
    private final ConversationQueryPort queries;
    private final Clock clock;

    @Autowired
    public NativeConversationService(NativeConversationPersistencePort persistence,
            ConversationQueryPort queries) {
        this(persistence, queries, Clock.systemUTC());
    }

    NativeConversationService(NativeConversationPersistencePort persistence,
            ConversationQueryPort queries, Clock clock) {
        this.persistence = persistence;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional
    public ConversationView createConversation(UUID workspaceId, String title) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (title != null && title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        UUID id = UUID.randomUUID();
        persistence.create(workspaceId, id, title, clock.instant());
        return getConversation(workspaceId, id);
    }

    @Transactional
    public ConversationView.MessageView appendMessage(UUID workspaceId, UUID conversationId,
            MessageRole role, List<MessageContentPart> content, GenerationMetadata generation) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(role, "role");
        content = List.copyOf(Objects.requireNonNull(content, "content"));
        if (content.isEmpty()) throw new IllegalArgumentException("content must not be empty");
        UUID messageId = UUID.randomUUID();
        var result = persistence.append(workspaceId, conversationId, messageId, role, content,
                generation, Map.of(), clock.instant());
        return queries.find(workspaceId, conversationId).orElseThrow(ConversationNotFoundException::new)
                .messages().get(result.sequenceNo());
    }

    @Transactional(readOnly = true)
    public ConversationView getConversation(UUID workspaceId, UUID conversationId) {
        return queries.find(workspaceId, conversationId).orElseThrow(ConversationNotFoundException::new);
    }
}
