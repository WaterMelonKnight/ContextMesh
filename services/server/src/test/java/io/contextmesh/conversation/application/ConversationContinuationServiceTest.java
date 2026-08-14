package io.contextmesh.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationContinuationServiceTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID cutoffId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    void createsEmptyNativeConversationAndRelationalOriginWithCutoff() {
        var queries = mock(ConversationQueryPort.class);
        var natives = mock(NativeConversationPersistencePort.class);
        var links = mock(ContinuationPersistencePort.class);
        var source = view(sourceId, ConversationSourceType.IMPORTED_CONVERSATION, "Imported title",
                List.of(message(cutoffId)));
        when(queries.find(workspace, sourceId)).thenReturn(Optional.of(source));
        var targetId = ArgumentCaptor.forClass(UUID.class);
        when(queries.find(org.mockito.ArgumentMatchers.eq(workspace), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(1).equals(sourceId)
                        ? Optional.of(source) : Optional.of(view(invocation.getArgument(1),
                        ConversationSourceType.NATIVE_CONVERSATION, "Imported title", List.of())));

        var result = new ConversationContinuationService(queries, natives, links,
                Clock.fixed(now, ZoneOffset.UTC)).create(workspace, sourceId, cutoffId, " ");

        verify(natives).create(org.mockito.ArgumentMatchers.eq(workspace), targetId.capture(),
                org.mockito.ArgumentMatchers.eq("Imported title"), org.mockito.ArgumentMatchers.eq(now));
        verify(links).create(workspace, targetId.getValue(), sourceId, cutoffId, now);
        assertThat(result.conversation().sourceType()).isEqualTo(ConversationSourceType.NATIVE_CONVERSATION);
        assertThat(result.conversation().messages()).isEmpty();
        assertThat(result.origin()).isEqualTo(new ContinuationOrigin(sourceId, cutoffId));
        assertThat(source.sourceType()).isEqualTo(ConversationSourceType.IMPORTED_CONVERSATION);
    }

    @Test
    void hidesMissingAndCrossConversationCutoffsAndRejectsNativeSources() {
        var queries = mock(ConversationQueryPort.class);
        var source = view(sourceId, ConversationSourceType.IMPORTED_CONVERSATION, "source", List.of());
        when(queries.find(workspace, sourceId)).thenReturn(Optional.empty());
        var service = new ConversationContinuationService(queries, mock(NativeConversationPersistencePort.class),
                mock(ContinuationPersistencePort.class), Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.create(workspace, sourceId, null, null))
                .isInstanceOf(ConversationNotFoundException.class);

        when(queries.find(workspace, sourceId)).thenReturn(Optional.of(source));
        assertThatThrownBy(() -> service.create(workspace, sourceId, UUID.randomUUID(), null))
                .isInstanceOf(ConversationNotFoundException.class);

        when(queries.find(workspace, sourceId)).thenReturn(Optional.of(
                view(sourceId, ConversationSourceType.NATIVE_CONVERSATION, "native", List.of())));
        assertThatThrownBy(() -> service.create(workspace, sourceId, null, null))
                .isInstanceOf(InvalidContinuationSourceException.class);
    }

    private ConversationView view(UUID id, ConversationSourceType type, String title,
            List<ConversationView.MessageView> messages) {
        return new ConversationView(id, workspace, type, null, null, title, null, null,
                now, now, Map.of(), messages);
    }

    private ConversationView.MessageView message(UUID id) {
        return new ConversationView.MessageView(id, id.toString(), 0, MessageRole.USER,
                List.of(new TextContentPart("source")), null, now, null, null, Map.of());
    }
}
