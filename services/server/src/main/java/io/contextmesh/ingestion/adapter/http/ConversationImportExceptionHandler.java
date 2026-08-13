package io.contextmesh.ingestion.adapter.http;

import io.contextmesh.ingestion.adapter.chatgpt.ChatGptExportException;
import io.contextmesh.ingestion.adapter.genericjson.GenericConversationJsonException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ConversationImportController.class, ChatGptImportController.class})
public final class ConversationImportExceptionHandler {
    @ExceptionHandler(GenericConversationJsonException.class)
    ProblemDetail invalidGenericJson(GenericConversationJsonException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setType(URI.create("https://contextmesh.io/problems/invalid-generic-conversation-json"));
        problem.setTitle("Invalid Generic Conversation JSON");
        return problem;
    }

    @ExceptionHandler(ChatGptExportException.class)
    ProblemDetail invalidChatGptExport(ChatGptExportException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setType(URI.create("https://contextmesh.io/problems/invalid-chatgpt-export"));
        problem.setTitle("Invalid ChatGPT Export");
        return problem;
    }
}
