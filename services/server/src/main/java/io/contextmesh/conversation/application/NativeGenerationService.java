package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelMessage;
import io.contextmesh.provider.application.ModelProviderRegistry;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public final class NativeGenerationService {
    private static final Object[] TURN_GUARDS = new Object[64];
    static { for (int i = 0; i < TURN_GUARDS.length; i++) TURN_GUARDS[i] = new Object(); }

    private final NativeConversationService conversations;
    private final ModelProviderRegistry providers;

    public NativeGenerationService(NativeConversationService conversations, ModelProviderRegistry providers) {
        this.conversations = conversations;
        this.providers = providers;
    }

    public GenerationStream generateTurn(UUID workspaceId, UUID conversationId, String providerId,
            String model, List<MessageContentPart> userContent) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(conversationId, "conversationId");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        userContent = List.copyOf(Objects.requireNonNull(userContent, "content"));
        if (userContent.isEmpty()) throw new IllegalArgumentException("content must not be empty");
        for (var part : userContent) if (!(part instanceof TextContentPart))
            throw new IllegalArgumentException("only TEXT content is supported");
        var provider = providers.resolve(providerId);
        if (conversations.getConversation(workspaceId, conversationId).sourceType()
                != ConversationSourceType.NATIVE_CONVERSATION)
            throw new ImportedConversationImmutableException();
        var content = userContent;
        return sink -> {
            synchronized (TURN_GUARDS[Math.floorMod(conversationId.hashCode(), TURN_GUARDS.length)]) {
                var request = requestAfterUserAppend(workspaceId, conversationId, model, content);
                runTurn(workspaceId, conversationId, model, provider.providerId(), provider.generate(request), sink);
            }
        };
    }

    private ModelGenerationRequest requestAfterUserAppend(UUID workspaceId, UUID conversationId,
            String model, List<MessageContentPart> content) {
        var before = conversations.getConversation(workspaceId, conversationId);
        if (before.sourceType() != ConversationSourceType.NATIVE_CONVERSATION)
            throw new ImportedConversationImmutableException();
        conversations.appendMessage(workspaceId, conversationId, MessageRole.USER, content, null);
        var after = conversations.getConversation(workspaceId, conversationId);
        return new ModelGenerationRequest(workspaceId, conversationId, model,
                after.messages().stream().map(this::modelMessage).toList());
    }

    private void runTurn(UUID workspaceId, UUID conversationId, String model,
            String providerId, GenerationStream providerStream,
            Consumer<GenerationEvent> sink) {
        var state = new StreamState();
        var observer = new EventObserver(sink);
        try {
            providerStream.consume(event -> acceptProviderEvent(event, state, observer, providerId, model));
        } catch (InvalidGenerationStreamException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (state.terminal)
                throw new InvalidGenerationStreamException("provider failed after a terminal event");
            observer.notify(new GenerationEvent.Failed("PROVIDER_FAILURE", "Model generation failed"));
            return;
        }
        if (!state.terminal) throw new InvalidGenerationStreamException("provider stream ended without a terminal event");
        if (state.failed) return;
        var assistant = conversations.appendMessage(workspaceId, conversationId, MessageRole.ASSISTANT,
                List.of(new TextContentPart(state.text.toString())),
                new GenerationMetadata(providerId, model));
        observer.notify(new GenerationEvent.Completed(providerId, model, assistant.id()));
    }

    private void acceptProviderEvent(GenerationEvent event, StreamState state, EventObserver observer,
            String providerId, String model) {
        Objects.requireNonNull(event, "provider event");
        if (state.terminal) throw new InvalidGenerationStreamException("event emitted after terminal event");
        if (event instanceof GenerationEvent.Started started) {
            if (state.started) throw new InvalidGenerationStreamException("STARTED must be emitted exactly once");
            if (!providerId.equals(started.provider()) || !model.equals(started.model()))
                throw new InvalidGenerationStreamException("STARTED provider/model does not match the request");
            state.started = true;
            observer.notify(started);
        } else if (event instanceof GenerationEvent.TextDelta delta) {
            if (!state.started) throw new InvalidGenerationStreamException("TEXT_DELTA emitted before STARTED");
            state.text.append(delta.text());
            observer.notify(delta);
        } else if (event instanceof GenerationEvent.Completed completed) {
            if (!state.started) throw new InvalidGenerationStreamException("COMPLETED emitted before STARTED");
            if (!providerId.equals(completed.provider()) || !model.equals(completed.model()))
                throw new InvalidGenerationStreamException("COMPLETED provider/model does not match the request");
            if (state.text.isEmpty()) throw new InvalidGenerationStreamException("COMPLETED requires text content");
            state.terminal = true;
        } else if (event instanceof GenerationEvent.Failed failed) {
            state.terminal = true;
            state.failed = true;
            observer.notify(failed);
        }
    }

    private ModelMessage modelMessage(ConversationView.MessageView message) {
        var role = switch (message.role()) {
            case SYSTEM -> ModelMessage.Role.SYSTEM;
            case USER -> ModelMessage.Role.USER;
            case ASSISTANT -> ModelMessage.Role.ASSISTANT;
            case TOOL -> throw new IllegalArgumentException("TOOL messages are not supported for generation");
        };
        var text = new StringBuilder();
        for (var part : message.content()) {
            if (!(part instanceof TextContentPart value))
                throw new IllegalArgumentException("only TEXT conversation context is supported");
            text.append(value.text());
        }
        return new ModelMessage(role, text.toString());
    }

    private static final class StreamState {
        final StringBuilder text = new StringBuilder();
        boolean started;
        boolean terminal;
        boolean failed;
    }

    /** Observer delivery is best-effort and cannot control provider execution or persistence. */
    private static final class EventObserver {
        private final Consumer<GenerationEvent> delegate;
        private boolean available = true;

        private EventObserver(Consumer<GenerationEvent> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "event observer");
        }

        private void notify(GenerationEvent event) {
            if (!available) return;
            try {
                delegate.accept(event);
            } catch (RuntimeException exception) {
                available = false;
            }
        }
    }
}
