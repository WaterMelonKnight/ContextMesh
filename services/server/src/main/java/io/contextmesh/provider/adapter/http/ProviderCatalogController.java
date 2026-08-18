package io.contextmesh.provider.adapter.http;

import io.contextmesh.provider.application.ModelProviderRegistry;
import io.contextmesh.provider.application.ProviderDescriptor;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only provider status.
 *
 * <p>The path is not workspace-scoped because provider configuration is process-wide server
 * configuration and is identical for every workspace; scoping it would imply a per-workspace
 * setting that does not exist. Nothing workspace-owned is returned.
 *
 * <p>The response describes configuration readiness only. It performs no upstream call, so it is
 * cheap, cannot be turned into a request amplifier, and never reports live endpoint health.
 */
@RestController
@RequestMapping("/api/v1/providers")
public final class ProviderCatalogController {
    private final ModelProviderRegistry registry;

    public ProviderCatalogController(ModelProviderRegistry registry) { this.registry = registry; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProviderDescriptor> list() { return registry.describeRegistered(); }
}
