package io.contextmesh.provider.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class ModelProviderRegistry {
    private final Map<String, ModelProvider> providers;

    public ModelProviderRegistry(List<ModelProvider> providers) {
        var indexed = new LinkedHashMap<String, ModelProvider>();
        for (var provider : providers) {
            if (provider.providerId() == null || provider.providerId().isBlank())
                throw new IllegalArgumentException("providerId must not be blank");
            if (indexed.putIfAbsent(provider.providerId(), provider) != null)
                throw new IllegalStateException("Duplicate model provider: " + provider.providerId());
        }
        this.providers = Map.copyOf(indexed);
    }

    public ModelProvider resolve(String providerId) {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("provider must not be blank");
        var provider = providers.get(providerId);
        if (provider == null) throw new UnknownModelProviderException(providerId);
        return provider;
    }
}
