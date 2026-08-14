package io.contextmesh.conversation.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoredContinuationContextResolver implements ContinuationContextResolver {
    private final ContinuationPersistencePort continuations;
    private final ConversationQueryPort conversations;

    public StoredContinuationContextResolver(ContinuationPersistencePort continuations,
            ConversationQueryPort conversations) {
        this.continuations = continuations;
        this.conversations = conversations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationView.MessageView> resolve(UUID workspaceId, UUID nativeConversationId) {
        var origin = continuations.findOrigin(workspaceId, nativeConversationId);
        if (origin.isEmpty()) return List.of();
        var value = origin.get();
        var messages = conversations.find(workspaceId, value.sourceConversationId())
                .orElseThrow(ConversationNotFoundException::new).messages();
        if (value.throughMessageId() == null) return messages;
        for (int i = 0; i < messages.size(); i++)
            if (messages.get(i).id().equals(value.throughMessageId())) return List.copyOf(messages.subList(0, i + 1));
        throw new ConversationNotFoundException();
    }
}
