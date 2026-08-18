import type { Provider } from "./types";

/**
 * Pure provider-selection rules for the composer. Only providers the server reports as registered
 * can be chosen: an unconfigured real provider is absent from the list and therefore unselectable.
 */

/** Starts on a deterministic local provider when one exists, so a page load never spends credit. */
export function defaultProviderId(providers: Provider[]): string {
  return (providers.find(provider => provider.kind === "BUILT_IN") ?? providers[0])?.id ?? "";
}

export function findProvider(providers: Provider[], id: string): Provider | undefined {
  return providers.find(provider => provider.id === id);
}

/**
 * Keeps a model the user typed. Only a still-untouched field — empty, or exactly the value this UI
 * prefilled for the previous provider — is replaced by the next provider's server-configured
 * default. Providers without a default leave the field empty rather than guessing a model ID.
 */
export function modelForProvider(previous: Provider | undefined, next: Provider | undefined, current: string): string {
  const prefilled = current === "" || current === (previous?.defaultModel ?? "");
  return prefilled ? next?.defaultModel ?? "" : current;
}

/** Explains a missing real provider without naming a credential or an endpoint. */
export function providerNotice(providers: Provider[]): string {
  if (providers.length === 0) return "No model provider is configured on the server.";
  if (!providers.some(provider => provider.kind === "EXTERNAL"))
    return "No real model provider is configured. Set the CONTEXTMESH_OPENAI_* environment variables on the server to enable one.";
  return "";
}
