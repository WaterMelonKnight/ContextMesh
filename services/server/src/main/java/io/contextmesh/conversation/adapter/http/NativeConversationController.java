package io.contextmesh.conversation.adapter.http;

import io.contextmesh.conversation.application.ConversationView;
import io.contextmesh.conversation.application.ContinuationResult;
import io.contextmesh.conversation.application.ConversationContinuationService;
import io.contextmesh.conversation.application.NativeConversationService;
import io.contextmesh.conversation.application.NativeGenerationService;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.conversation.domain.ContentPartType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/conversations")
public final class NativeConversationController {
    private final NativeConversationService service;
    private final NativeGenerationService generationService;
    private final ConversationContinuationService continuationService;

    public NativeConversationController(NativeConversationService service,
            NativeGenerationService generationService, ConversationContinuationService continuationService) {
        this.service = service;
        this.generationService = generationService;
        this.continuationService = continuationService;
    }

    @PostMapping(value = "/{sourceConversationId}/continuations",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ContinuationResult continueConversation(@PathVariable UUID workspaceId,
            @PathVariable UUID sourceConversationId, @RequestBody(required = false) ContinuationRequest request) {
        return continuationService.create(workspaceId, sourceConversationId,
                request == null ? null : request.throughMessageId(), request == null ? null : request.title());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView create(@PathVariable UUID workspaceId,
            @RequestBody CreateConversationRequest request) {
        return service.createConversation(workspaceId, request.title());
    }

    @PostMapping(value = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationView.MessageView append(@PathVariable UUID workspaceId,
            @PathVariable UUID conversationId, @RequestBody AppendMessageRequest request) {
        if (request == null || request.role() == null) throw new IllegalArgumentException("role is required");
        if (request.content() == null || request.content().isEmpty())
            throw new IllegalArgumentException("content must not be empty");
        List<MessageContentPart> content = request.content().stream().map(part -> {
            if (part.type() != ContentPartType.TEXT) throw new IllegalArgumentException("only TEXT content is supported");
            return (MessageContentPart) new TextContentPart(part.text());
        }).toList();
        GenerationMetadata generation = request.generation() == null ? null
                : new GenerationMetadata(request.generation().provider(), request.generation().model());
        return service.appendMessage(workspaceId, conversationId, request.role(), content, generation);
    }

    @GetMapping(value = "/{conversationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversationView get(@PathVariable UUID workspaceId, @PathVariable UUID conversationId) {
        return service.getConversation(workspaceId, conversationId);
    }

    @PostMapping(value = "/{conversationId}/turns", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter turn(@PathVariable UUID workspaceId, @PathVariable UUID conversationId,
            @RequestBody TurnRequest request) {
        if (request == null || request.content() == null || request.content().isEmpty())
            throw new IllegalArgumentException("content must not be empty");
        List<MessageContentPart> content = request.content().stream().map(part -> {
            if (part.type() != ContentPartType.TEXT)
                throw new IllegalArgumentException("only TEXT content is supported");
            return (MessageContentPart) new TextContentPart(part.text());
        }).toList();
        var stream = generationService.generateTurn(workspaceId, conversationId,
                request.provider(), request.model(), content);
        var emitter = new SseEmitter(0L);
        var delivery = new SseDelivery(emitter);
        Thread.startVirtualThread(() -> {
            try {
                stream.consume(delivery::send);
            } catch (RuntimeException exception) {
                delivery.send(new GenerationEvent.Failed("GENERATION_ERROR", "Generation could not be completed"));
            } finally {
                delivery.complete();
            }
        });
        return emitter;
    }

    private static final class SseDelivery {
        private final SseEmitter emitter;
        private boolean available = true;

        private SseDelivery(SseEmitter emitter) { this.emitter = emitter; }

        private void send(GenerationEvent event) {
            if (!available) return;
            try {
                String name = switch (event) {
                    case GenerationEvent.Started ignored -> "started";
                    case GenerationEvent.TextDelta ignored -> "delta";
                    case GenerationEvent.Completed ignored -> "completed";
                    case GenerationEvent.Failed ignored -> "failed";
                };
                emitter.send(SseEmitter.event().name(name).data(event));
            } catch (java.io.IOException | IllegalStateException exception) {
                available = false;
            }
        }

        private void complete() {
            if (!available) return;
            available = false;
            emitter.complete();
        }
    }

    public record CreateConversationRequest(String title) {}
    public record AppendMessageRequest(MessageRole role, List<ContentPartRequest> content,
            GenerationRequest generation) {}
    public record ContentPartRequest(ContentPartType type, String text) {}
    public record GenerationRequest(String provider, String model) {}
    public record TurnRequest(String provider, String model, List<ContentPartRequest> content) {}
    public record ContinuationRequest(UUID throughMessageId, String title) {}
}
