package io.contextmesh.conversation.application;

public final class InvalidContinuationSourceException extends RuntimeException {
    public InvalidContinuationSourceException() {
        super("Only an imported conversation can be continued");
    }
}
