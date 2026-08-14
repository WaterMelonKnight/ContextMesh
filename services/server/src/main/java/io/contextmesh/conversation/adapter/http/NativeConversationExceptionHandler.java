package io.contextmesh.conversation.adapter.http;

import io.contextmesh.conversation.application.ConversationNotFoundException;
import io.contextmesh.conversation.application.ImportedConversationImmutableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NativeConversationController.class)
public final class NativeConversationExceptionHandler {
    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail notFound(ConversationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Conversation Not Found", exception.getMessage(),
                "conversation-not-found");
    }

    @ExceptionHandler(ImportedConversationImmutableException.class)
    ProblemDetail immutable(ImportedConversationImmutableException exception) {
        return problem(HttpStatus.CONFLICT, "Imported Conversation Is Immutable", exception.getMessage(),
                "imported-conversation-immutable");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid(Exception exception) {
        String detail = exception instanceof IllegalArgumentException ? exception.getMessage()
                : "Request JSON contains an invalid or unknown value";
        return problem(HttpStatus.BAD_REQUEST, "Invalid Native Conversation Request", detail,
                "invalid-native-conversation-request");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://contextmesh.io/problems/" + type));
        return problem;
    }
}
