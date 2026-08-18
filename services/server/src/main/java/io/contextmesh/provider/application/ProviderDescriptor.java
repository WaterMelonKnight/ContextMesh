package io.contextmesh.provider.application;

import java.util.Objects;

/**
 * Non-secret description of one registered provider.
 *
 * <p>Registration is the availability contract: a provider bean exists only when it is enabled and
 * validly configured, so every descriptor describes a provider that can be selected for a turn. The
 * descriptor reports configuration readiness and never probes the upstream endpoint, so it says
 * nothing about current reachability.
 *
 * <p>Every component is safe to return to a browser. Credentials, authorization headers, base URLs,
 * and arbitrary default headers are deliberately absent, and no component may be added that could
 * carry them.
 */
public record ProviderDescriptor(String id, String displayName, ProviderKind kind, String defaultModel) {
    public ProviderDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("provider id must not be blank");
        Objects.requireNonNull(kind, "kind");
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        defaultModel = defaultModel == null || defaultModel.isBlank() ? null : defaultModel.strip();
    }
}
