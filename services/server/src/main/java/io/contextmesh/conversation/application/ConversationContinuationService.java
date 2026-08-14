package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.ConversationSourceType;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationContinuationService {
    private final ConversationQueryPort queries;
    private final NativeConversationPersistencePort nativePersistence;
    private final ContinuationPersistencePort continuations;
    private final Clock clock;

    public ConversationContinuationService(ConversationQueryPort queries,
            NativeConversationPersistencePort nativePersistence,
            ContinuationPersistencePort continuations) {
        this(queries, nativePersistence, continuations, Clock.systemUTC());
    }

    ConversationContinuationService(ConversationQueryPort queries,
            NativeConversationPersistencePort nativePersistence,
            ContinuationPersistencePort continuations, Clock clock) {
        this.queries = queries;
        this.nativePersistence = nativePersistence;
        this.continuations = continuations;
        this.clock = clock;
    }

    @Transactional
    public ContinuationResult create(UUID workspaceId, UUID sourceConversationId,
            UUID throughMessageId, String title) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(sourceConversationId, "sourceConversationId");
        var source = queries.find(workspaceId, sourceConversationId)
                .orElseThrow(ConversationNotFoundException::new);
        if (source.sourceType() != ConversationSourceType.IMPORTED_CONVERSATION)
            throw new InvalidContinuationSourceException();
        if (throughMessageId != null && source.messages().stream().noneMatch(m -> m.id().equals(throughMessageId)))
            throw new ConversationNotFoundException();
        String resolvedTitle = title == null || title.isBlank() ? source.title() : title;
        if (resolvedTitle == null || resolvedTitle.isBlank()) resolvedTitle = "Imported conversation continuation";
        UUID targetId = UUID.randomUUID();
        var now = clock.instant();
        nativePersistence.create(workspaceId, targetId, resolvedTitle, now);
        continuations.create(workspaceId, targetId, sourceConversationId, throughMessageId, now);
        var target = queries.find(workspaceId, targetId).orElseThrow(ConversationNotFoundException::new);
        return new ContinuationResult(target, new ContinuationOrigin(sourceConversationId, throughMessageId));
    }
}
