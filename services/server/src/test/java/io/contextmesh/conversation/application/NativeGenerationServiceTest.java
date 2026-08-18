package io.contextmesh.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelProvider;
import io.contextmesh.provider.application.ModelProviderException;
import io.contextmesh.provider.application.ModelProviderRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NativeGenerationServiceTest {
    private final UUID workspace = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();

    @Test
    void sendsOrderedHistoryAggregatesDeltasAndPersistsGeneratedMetadataAndParent() {
        var conversations = mock(NativeConversationService.class);
        var captured = new AtomicReference<ModelGenerationRequest>();
        ModelProvider provider = provider(captured, false);
        var prior = message(UUID.randomUUID(), "prior", 0, MessageRole.ASSISTANT, "Earlier", null, null);
        var user = message(UUID.randomUUID(), "user", 1, MessageRole.USER, "Hello", "prior", null);
        var assistant = message(UUID.randomUUID(), "assistant", 2, MessageRole.ASSISTANT,
                "Fake response", "user", new GenerationMetadata("fake", "fake-model"));
        when(conversations.getConversation(workspace, conversation))
                .thenReturn(view(ConversationSourceType.NATIVE_CONVERSATION, List.of(prior)),
                        view(ConversationSourceType.NATIVE_CONVERSATION, List.of(prior)),
                        view(ConversationSourceType.NATIVE_CONVERSATION, List.of(prior, user)));
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.USER), any(), eq(null)))
                .thenReturn(user);
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.ASSISTANT), any(),
                eq(new GenerationMetadata("fake", "fake-model")))).thenReturn(assistant);

        var events = new ArrayList<GenerationEvent>();
        new NativeGenerationService(conversations, new ModelProviderRegistry(List.of(provider)), (w, c) -> List.of())
                .generateTurn(workspace, conversation, "fake", "fake-model", List.of(new TextContentPart("Hello")))
                .consume(events::add);

        assertThat(captured.get().messages()).extracting(message -> message.text())
                .containsExactly("Earlier", "Hello");
        assertThat(events).containsExactly(new GenerationEvent.Started("fake", "fake-model"),
                new GenerationEvent.TextDelta("Fake "), new GenerationEvent.TextDelta("response"),
                new GenerationEvent.Completed("fake", "fake-model", assistant.id()));
        verify(conversations).appendMessage(workspace, conversation, MessageRole.ASSISTANT,
                List.of(new TextContentPart("Fake response")), new GenerationMetadata("fake", "fake-model"));
        assertThat(assistant.parentExternalId()).isEqualTo(user.stableId());
    }

    @Test
    void prependsSelectedImportedContextWithoutPersistingIt() {
        var conversations = mock(NativeConversationService.class);
        var captured = new AtomicReference<ModelGenerationRequest>();
        var imported = List.of(
                message(UUID.randomUUID(), "i0", 0, MessageRole.SYSTEM, "System", null, null),
                message(UUID.randomUUID(), "i1", 1, MessageRole.USER, "Imported question", "i0", null));
        var empty = view(ConversationSourceType.NATIVE_CONVERSATION, List.of());
        var user = message(UUID.randomUUID(), "n0", 0, MessageRole.USER, "Continue", null, null);
        var assistant = message(UUID.randomUUID(), "n1", 1, MessageRole.ASSISTANT,
                "Fake response", "n0", new GenerationMetadata("fake", "fake-model"));
        when(conversations.getConversation(workspace, conversation)).thenReturn(empty, empty,
                view(ConversationSourceType.NATIVE_CONVERSATION, List.of(user)));
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.USER), any(), eq(null)))
                .thenReturn(user);
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.ASSISTANT), any(), any()))
                .thenReturn(assistant);

        new NativeGenerationService(conversations,
                new ModelProviderRegistry(List.of(provider(captured, false))), (w, c) -> imported)
                .generateTurn(workspace, conversation, "fake", "fake-model",
                        List.of(new TextContentPart("Continue"))).consume(event -> {});

        assertThat(captured.get().messages()).extracting(io.contextmesh.provider.application.ModelMessage::text)
                .containsExactly("System", "Imported question", "Continue");
        verify(conversations, org.mockito.Mockito.times(2)).appendMessage(eq(workspace), eq(conversation),
                any(), any(), any());
    }

    @Test
    void providerFailureKeepsUserAndDoesNotAppendAssistant() {
        var conversations = mock(NativeConversationService.class);
        var prior = view(ConversationSourceType.NATIVE_CONVERSATION, List.of());
        var user = message(UUID.randomUUID(), "user", 0, MessageRole.USER, "Hello", null, null);
        when(conversations.getConversation(workspace, conversation)).thenReturn(prior, prior,
                view(ConversationSourceType.NATIVE_CONVERSATION, List.of(user)));
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.USER), any(), eq(null)))
                .thenReturn(user);
        var events = new ArrayList<GenerationEvent>();
        new NativeGenerationService(conversations, new ModelProviderRegistry(List.of(provider(null, true))), (w, c) -> List.of())
                .generateTurn(workspace, conversation, "fake", "fake-model", List.of(new TextContentPart("Hello")))
                .consume(events::add);
        assertThat(events).containsExactly(new GenerationEvent.Started("fake", "fake-model"),
                new GenerationEvent.TextDelta("partial"),
                new GenerationEvent.Failed("PROVIDER_FAILURE", "Model generation failed"));
        verify(conversations).appendMessage(workspace, conversation, MessageRole.USER,
                List.of(new TextContentPart("Hello")), null);
        verify(conversations, org.mockito.Mockito.never()).appendMessage(eq(workspace), eq(conversation),
                eq(MessageRole.ASSISTANT), any(), any());
    }

    @Test
    void observerFailureDuringTextDeltaDoesNotPreventAssistantPersistence() {
        var fixture = successfulFixture(provider(null, false));
        var observed = new ArrayList<GenerationEvent>();

        fixture.service().generateTurn(workspace, conversation, "fake", "fake-model",
                List.of(new TextContentPart("Hello"))).consume(event -> {
                    observed.add(event);
                    if (event instanceof GenerationEvent.TextDelta) throw new IllegalStateException("disconnected");
                });

        assertThat(observed).containsExactly(new GenerationEvent.Started("fake", "fake-model"),
                new GenerationEvent.TextDelta("Fake "));
        verify(fixture.conversations()).appendMessage(workspace, conversation, MessageRole.ASSISTANT,
                List.of(new TextContentPart("Fake response")), new GenerationMetadata("fake", "fake-model"));
    }

    @Test
    void observerFailureDuringStartedDoesNotPreventAssistantPersistence() {
        var fixture = successfulFixture(provider(null, false));
        var observed = new ArrayList<GenerationEvent>();

        fixture.service().generateTurn(workspace, conversation, "fake", "fake-model",
                List.of(new TextContentPart("Hello"))).consume(event -> {
                    observed.add(event);
                    throw new IllegalStateException("disconnected");
                });

        assertThat(observed).containsExactly(new GenerationEvent.Started("fake", "fake-model"));
        verify(fixture.conversations()).appendMessage(workspace, conversation, MessageRole.ASSISTANT,
                List.of(new TextContentPart("Fake response")), new GenerationMetadata("fake", "fake-model"));
    }

    @Test
    void invalidProviderEventOrderingDoesNotPersistAssistant() {
        ModelProvider invalid = new ModelProvider() {
            public String providerId() { return "fake"; }
            public io.contextmesh.provider.application.GenerationStream generate(ModelGenerationRequest request) {
                return sink -> sink.accept(new GenerationEvent.TextDelta("out of order"));
            }
        };
        var fixture = successfulFixture(invalid);

        assertThatThrownBy(() -> fixture.service().generateTurn(workspace, conversation, "fake", "fake-model",
                List.of(new TextContentPart("Hello"))).consume(event -> {}))
                .isInstanceOf(InvalidGenerationStreamException.class)
                .hasMessage("TEXT_DELTA emitted before STARTED");
        verify(fixture.conversations(), org.mockito.Mockito.never()).appendMessage(eq(workspace), eq(conversation),
                eq(MessageRole.ASSISTANT), any(), any());
    }

    @Test
    void rejectsImportedConversationAndEmptyInput() {
        var conversations = mock(NativeConversationService.class);
        when(conversations.getConversation(workspace, conversation))
                .thenReturn(view(ConversationSourceType.IMPORTED_CONVERSATION, List.of()));
        var service = new NativeGenerationService(conversations,
                new ModelProviderRegistry(List.of(provider(null, false))), (w, c) -> List.of());
        assertThatThrownBy(() -> service.generateTurn(workspace, conversation, "fake", "fake-model",
                List.of(new TextContentPart("Hello"))))
                .isInstanceOf(ImportedConversationImmutableException.class);
        assertThatThrownBy(() -> service.generateTurn(workspace, conversation, "fake", "fake-model", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("content must not be empty");
    }

    @Test
    void reportsProviderReasonWithoutAdapterDetailAndWithoutPersistingAssistant() {
        var events = new ArrayList<GenerationEvent>();
        var fixture = successfulFixture(failing(new TestProviderFailure(ModelProviderException.Reason.AUTHENTICATION,
                "Bearer sk-live-secret was rejected by upstream")));

        fixture.service().generateTurn(workspace, conversation, "fake", "fake-model",
                List.of(new TextContentPart("Hello"))).consume(events::add);

        assertThat(events).last().isEqualTo(
                new GenerationEvent.Failed("PROVIDER_AUTHENTICATION", "Provider authentication failed."));
        assertThat(events.toString()).doesNotContain("sk-live-secret");
        verify(fixture.conversations(), org.mockito.Mockito.never()).appendMessage(eq(workspace), eq(conversation),
                eq(MessageRole.ASSISTANT), any(), any());
    }

    @Test
    void mapsEveryProviderReasonToAStableNonSecretCode() {
        var codes = new ArrayList<String>();
        for (var reason : ModelProviderException.Reason.values()) {
            var events = new ArrayList<GenerationEvent>();
            successfulFixture(failing(new TestProviderFailure(reason, "upstream detail")))
                    .service().generateTurn(workspace, conversation, "fake", "fake-model",
                            List.of(new TextContentPart("Hello"))).consume(events::add);
            var failed = (GenerationEvent.Failed) events.getLast();
            assertThat(failed.message()).doesNotContain("upstream detail").endsWith(".");
            codes.add(failed.code());
        }
        assertThat(codes).containsExactly("PROVIDER_AUTHENTICATION", "PROVIDER_RATE_LIMIT",
                "PROVIDER_UNAVAILABLE", "PROVIDER_PROTOCOL");
    }

    /** Any adapter's failure, without depending on one adapter's exception types. */
    private static final class TestProviderFailure extends ModelProviderException {
        private TestProviderFailure(Reason reason, String message) { super(reason, message); }
    }

    private ModelProvider failing(RuntimeException failure) {
        return new ModelProvider() {
            public String providerId() { return "fake"; }
            public io.contextmesh.provider.application.GenerationStream generate(ModelGenerationRequest request) {
                return sink -> {
                    sink.accept(new GenerationEvent.Started("fake", request.model()));
                    sink.accept(new GenerationEvent.TextDelta("partial"));
                    throw failure;
                };
            }
        };
    }

    private ModelProvider provider(AtomicReference<ModelGenerationRequest> captured, boolean fail) {
        return new ModelProvider() {
            public String providerId() { return "fake"; }
            public io.contextmesh.provider.application.GenerationStream generate(ModelGenerationRequest request) {
                if (captured != null) captured.set(request);
                return sink -> {
                    sink.accept(new GenerationEvent.Started("fake", request.model()));
                    sink.accept(new GenerationEvent.TextDelta(fail ? "partial" : "Fake "));
                    if (fail) throw new IllegalStateException("secret provider detail");
                    sink.accept(new GenerationEvent.TextDelta("response"));
                    sink.accept(new GenerationEvent.Completed("fake", request.model(), null));
                };
            }
        };
    }

    private Fixture successfulFixture(ModelProvider provider) {
        var conversations = mock(NativeConversationService.class);
        var empty = view(ConversationSourceType.NATIVE_CONVERSATION, List.of());
        var user = message(UUID.randomUUID(), "user", 0, MessageRole.USER, "Hello", null, null);
        var assistant = message(UUID.randomUUID(), "assistant", 1, MessageRole.ASSISTANT,
                "Fake response", "user", new GenerationMetadata("fake", "fake-model"));
        when(conversations.getConversation(workspace, conversation)).thenReturn(empty, empty,
                view(ConversationSourceType.NATIVE_CONVERSATION, List.of(user)));
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.USER), any(), eq(null)))
                .thenReturn(user);
        when(conversations.appendMessage(eq(workspace), eq(conversation), eq(MessageRole.ASSISTANT), any(),
                eq(new GenerationMetadata("fake", "fake-model")))).thenReturn(assistant);
        return new Fixture(new NativeGenerationService(conversations,
                new ModelProviderRegistry(List.of(provider)), (w, c) -> List.of()), conversations);
    }

    private record Fixture(NativeGenerationService service, NativeConversationService conversations) {}

    private ConversationView view(ConversationSourceType type, List<ConversationView.MessageView> messages) {
        return new ConversationView(conversation, workspace, type, null, null, null, null, null,
                Instant.EPOCH, Instant.EPOCH, Map.of(), messages);
    }
    private ConversationView.MessageView message(UUID id, String stable, int sequence, MessageRole role,
            String text, String parent, GenerationMetadata generation) {
        return new ConversationView.MessageView(id, stable, sequence, role, List.of(new TextContentPart(text)),
                null, Instant.EPOCH, parent, generation, Map.of());
    }
}
