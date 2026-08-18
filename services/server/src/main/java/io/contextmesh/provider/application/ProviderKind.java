package io.contextmesh.provider.application;

/**
 * How a registered provider executes, so clients can distinguish local development execution from
 * calls that leave the machine without hard-coding provider identifiers.
 */
public enum ProviderKind {
    /** Deterministic in-process provider for development and tests; contacts no network. */
    BUILT_IN,
    /** Calls an external model API configured by the server operator. */
    EXTERNAL
}
