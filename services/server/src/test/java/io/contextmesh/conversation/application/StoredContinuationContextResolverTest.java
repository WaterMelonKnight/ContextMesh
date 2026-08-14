package io.contextmesh.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredContinuationContextResolverTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();

    @Test
    void returnsFullHistoryWithoutCutoffAndInclusivePrefixWithCutoff() {
        var links = mock(ContinuationPersistencePort.class);
        var conversations = mock(ConversationQueryPort.class);
        var messages = List.of(message(0), message(1), message(2));
        when(conversations.find(workspace, source)).thenReturn(Optional.of(new ConversationView(
                source, workspace, ConversationSourceType.IMPORTED_CONVERSATION, null, null, "source",
                null, null, Instant.EPOCH, Instant.EPOCH, Map.of(), messages)));
        var resolver = new StoredContinuationContextResolver(links, conversations);

        when(links.findOrigin(workspace, target)).thenReturn(Optional.of(new ContinuationOrigin(source, null)));
        assertThat(resolver.resolve(workspace, target)).extracting(ConversationView.MessageView::sequenceNo)
                .containsExactly(0, 1, 2);

        when(links.findOrigin(workspace, target))
                .thenReturn(Optional.of(new ContinuationOrigin(source, messages.get(1).id())));
        assertThat(resolver.resolve(workspace, target)).extracting(ConversationView.MessageView::sequenceNo)
                .containsExactly(0, 1);
    }

    @Test
    void ordinaryNativeConversationHasNoImportedContext() {
        var links = mock(ContinuationPersistencePort.class);
        when(links.findOrigin(workspace, target)).thenReturn(Optional.empty());
        assertThat(new StoredContinuationContextResolver(links, mock(ConversationQueryPort.class))
                .resolve(workspace, target)).isEmpty();
    }

    private ConversationView.MessageView message(int sequence) {
        UUID id = UUID.randomUUID();
        return new ConversationView.MessageView(id, id.toString(), sequence, MessageRole.USER,
                List.of(new TextContentPart("message " + sequence)), null, Instant.EPOCH,
                null, null, Map.of());
    }
}
