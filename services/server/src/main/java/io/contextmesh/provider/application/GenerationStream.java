package io.contextmesh.provider.application;

import java.util.function.Consumer;

/** A synchronous subscription contract whose events are delivered incrementally in call order. */
@FunctionalInterface
public interface GenerationStream {
    void consume(Consumer<GenerationEvent> consumer);
}
