import assert from "node:assert/strict";
import { test } from "node:test";
import { defaultProviderId, findProvider, modelForProvider, providerNotice } from "./providers.ts";
import type { Provider } from "./types.ts";

const fake: Provider = { id: "fake", displayName: "Fake (local, deterministic)", kind: "BUILT_IN", defaultModel: "fake-model" };
const openai: Provider = { id: "openai-compatible", displayName: "OpenAI-compatible", kind: "EXTERNAL", defaultModel: null };
const configured: Provider = { ...openai, defaultModel: "configured-model" };

test("choices come from the backend payload only, so an unconfigured provider is unselectable", () => {
  // The server omits providers it has not registered; nothing in the UI can add one back.
  const fromServer = [fake];
  assert.equal(findProvider(fromServer, "openai-compatible"), undefined);
  assert.deepEqual(fromServer.map(provider => provider.id), ["fake"]);
  assert.equal(defaultProviderId(fromServer), "fake");
});

test("the built-in fake provider stays selectable and is preferred as the default", () => {
  assert.equal(defaultProviderId([openai, fake]), "fake");
  assert.equal(findProvider([openai, fake], "fake"), fake);
});

test("falls back to the first available provider when no built-in one is registered", () => {
  assert.equal(defaultProviderId([openai, configured]), "openai-compatible");
  assert.equal(defaultProviderId([]), "");
});

test("model stays editable: a user-entered model survives a provider change", () => {
  assert.equal(modelForProvider(fake, openai, "my-own-model"), "my-own-model");
  assert.equal(modelForProvider(undefined, configured, "typed-first"), "typed-first");
});

test("only a prefilled or empty model field is replaced by the next provider default", () => {
  assert.equal(modelForProvider(fake, configured, "fake-model"), "configured-model");
  assert.equal(modelForProvider(undefined, fake, ""), "fake-model");
  // No server default means an empty field, never a guessed model ID.
  assert.equal(modelForProvider(fake, openai, "fake-model"), "");
});

test("explains a missing real provider without naming a credential value", () => {
  assert.match(providerNotice([fake]), /CONTEXTMESH_OPENAI_\*/);
  assert.equal(providerNotice([fake, openai]), "");
  assert.equal(providerNotice([]), "No model provider is configured on the server.");
});
