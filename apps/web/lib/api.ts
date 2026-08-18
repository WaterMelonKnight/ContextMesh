import { consumeSse, responseError } from "./sse";
import type { Conversation, ConversationSummary, Origin } from "./types";
// Same-origin paths only: the Next.js rewrite in next.config.mjs proxies /api/** to the backend,
// so the browser never needs a public Spring Boot hostname or port.
async function json<T>(path: string, init?: RequestInit): Promise<T> { const response = await fetch(path, init); if (!response.ok) throw new Error(await responseError(response)); return response.json() as Promise<T>; }
const path = (workspace: string) => `/api/v1/workspaces/${workspace}`;
export const api = {
  bootstrap: () => json<{ id: string; name: string }>("/api/v1/development/workspace"),
  list: (workspace: string) => json<ConversationSummary[]>(`${path(workspace)}/conversations`),
  get: (workspace: string, id: string) => json<Conversation>(`${path(workspace)}/conversations/${id}`),
  importChatGpt: (workspace: string, body: string) => json<{ importedCount: number; skippedDuplicateCount: number }>(`${path(workspace)}/imports/chatgpt`, { method: "POST", headers: { "Content-Type": "application/json" }, body }),
  continue: (workspace: string, id: string, throughMessageId?: string) => json<{ conversation: Conversation; origin: Origin }>(`${path(workspace)}/conversations/${id}/continuations`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(throughMessageId ? { throughMessageId } : {}) }),
  turn: async (workspace: string, id: string, provider: string, model: string, message: string, event: Parameters<typeof consumeSse>[1]) => { const response = await fetch(`${path(workspace)}/conversations/${id}/turns`, { method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" }, body: JSON.stringify({ provider, model, content: [{ type: "TEXT", text: message }] }) }); await consumeSse(response, event); },
};
