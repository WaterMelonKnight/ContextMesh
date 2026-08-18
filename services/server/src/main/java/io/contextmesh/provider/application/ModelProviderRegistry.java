package io.contextmesh.provider.application;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public final class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers;

    public ModelProviderRegistry(List<ModelProvider> providers) {
        // Sorted by identifier so resolution and the status API stay deterministic regardless of
        // bean discovery order.
        var indexed = new TreeMap<String, ModelProvider>();
        for (var provider : providers) {
            if (provider.providerId() == null || provider.providerId().isBlank())
                throw new IllegalArgumentException("providerId must not be blank");
            if (indexed.putIfAbsent(provider.providerId(), provider) != null)
                throw new IllegalStateException("Duplicate model provider: " + provider.providerId());
        }
        this.providers = Collections.unmodifiableSortedMap(indexed);
    }

    public ModelProvider resolve(String providerId) {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("provider must not be blank");
        var provider = providers.get(providerId);
        if (provider == null) throw new UnknownModelProviderException(providerId);
        return provider;
    }

    /** Describes exactly the providers {@link #resolve(String)} accepts, in identifier order. */
    public List<ProviderDescriptor> describeRegistered() {
        return providers.values().stream().map(ModelProvider::describe).toList();
    }
}
