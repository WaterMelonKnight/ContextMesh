"use client";
import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api";
import { defaultProviderId, findProvider, modelForProvider, providerNotice } from "../lib/providers";
import type { Conversation, ConversationSummary, Message, Provider } from "../lib/types";
export default function Home() {
  const [workspace, setWorkspace] = useState(""); const [items, setItems] = useState<ConversationSummary[]>([]); const [selected, setSelected] = useState<Conversation | null>(null); const [error, setError] = useState(""); const [notice, setNotice] = useState(""); const [stream, setStream] = useState(""); const [generating, setGenerating] = useState(false);
  // null until the provider status API answers, so the composer never claims "not configured" while loading.
  const [providers, setProviders] = useState<Provider[] | null>(null);
  const refresh = useCallback(async (id = workspace) => { if (id) setItems(await api.list(id)); }, [workspace]);
  const open = useCallback(async (id: string) => { if (workspace) { setSelected(await api.get(workspace, id)); setStream(""); } }, [workspace]);
  useEffect(() => { api.bootstrap().then(async value => { setWorkspace(value.id); await refresh(value.id); }).catch(reason => setError(reason instanceof Error ? reason.message : "Backend unavailable")); }, [refresh]);
  useEffect(() => { api.providers().then(setProviders).catch(reason => setError(reason instanceof Error ? reason.message : "Provider status unavailable")); }, []);
  async function importFile(file: File) { try { setError(""); const result = await api.importChatGpt(workspace, await file.text()); setNotice(`Imported ${result.importedCount}; skipped ${result.skippedDuplicateCount}.`); await refresh(); } catch (reason) { setError(reason instanceof Error ? reason.message : "Import failed"); } }
  async function continueFrom(message?: Message) { if (!selected) return; try { const result = await api.continue(workspace, selected.id, message?.id); await refresh(); setSelected(result.conversation); setNotice("Native continuation created."); } catch (reason) { setError(reason instanceof Error ? reason.message : "Continuation failed"); } }
  // The server sends a stable, non-secret failure message; show it instead of a generic sentence.
  async function send(provider: string, model: string, content: string) { if (!selected) return; setGenerating(true); setStream(""); setError(""); try { await api.turn(workspace, selected.id, provider, model, content, ({ event, data }) => { if (event === "delta") setStream(value => value + ((data as { text?: string }).text ?? "")); if (event === "failed") setError((data as { message?: string }).message || "Generation could not be completed."); }); } catch (reason) { setError(reason instanceof Error ? reason.message : "Generation failed"); } finally { setGenerating(false); await open(selected.id); await refresh(); } }
  return <main className="shell"><aside><header><p className="eyebrow">Local MVP</p><h1>ContextMesh</h1><small>{workspace ? `Workspace ${workspace.slice(0, 8)}…` : "Connecting…"}</small></header><label className="import">Import ChatGPT JSON<input type="file" accept="application/json,.json" disabled={!workspace} onChange={event => { const file = event.target.files?.[0]; if (file) void importFile(file); }} /></label><h2>Conversations</h2><nav>{items.map(item => <button className={selected?.id === item.id ? "active" : ""} key={item.id} onClick={() => void open(item.id)}><strong>{item.title || "Untitled"}</strong><span>{item.sourceType === "IMPORTED_CONVERSATION" ? item.sourceProvider || "Imported" : item.origin ? "Continuation" : "Native"}</span></button>)}</nav></aside><section className="detail">{error && <p className="alert error">{error}</p>}{notice && <p className="alert">{notice}</p>}{!selected ? <div className="empty"><h2>Import or choose a conversation</h2><p>Your database remains the source of truth.</p></div> : <><div className="detailHeader"><div><p className="eyebrow">{selected.sourceType === "IMPORTED_CONVERSATION" ? `Imported · ${selected.sourceProvider}` : "Native conversation"}</p><h2>{selected.title || "Untitled"}</h2></div>{selected.sourceType === "IMPORTED_CONVERSATION" && <button onClick={() => void continueFrom()}>Continue full conversation</button>}</div><div className="messages">{selected.messages.map(message => <article key={message.id} className={message.role.toLowerCase()}><div><b>{message.role}</b>{selected.sourceType === "IMPORTED_CONVERSATION" && <button className="link" onClick={() => void continueFrom(message)}>Continue from here</button>}</div>{message.content.map((part, index) => <p key={index}>{part.text}</p>)}</article>)}{generating && <article className="assistant streaming"><div><b>ASSISTANT</b><span> streaming</span></div><p>{stream || "…"}</p></article>}</div>{selected.sourceType === "NATIVE_CONVERSATION" && (providers === null ? <p className="composerHint">Loading configured providers…</p> : <Composer disabled={generating} providers={providers} onSend={send} />)}</>}</section></main>;
}
function Composer({ disabled, providers, onSend }: { disabled: boolean; providers: Provider[]; onSend: (provider: string, model: string, content: string) => Promise<void> }) {
  const [provider, setProvider] = useState(() => defaultProviderId(providers));
  const [model, setModel] = useState(() => findProvider(providers, defaultProviderId(providers))?.defaultModel ?? "");
  const [content, setContent] = useState("");
  const hint = providerNotice(providers);
  function chooseProvider(id: string) {
    setModel(current => modelForProvider(findProvider(providers, provider), findProvider(providers, id), current));
    setProvider(id);
  }
  return <form className="composer" onSubmit={event => { event.preventDefault(); if (provider && model.trim() && content.trim()) void onSend(provider, model.trim(), content).then(() => setContent("")); }}>
    <div>
      <label>Provider<select aria-label="Provider" value={provider} disabled={providers.length === 0} onChange={event => chooseProvider(event.target.value)}>{providers.map(option => <option key={option.id} value={option.id}>{option.displayName}</option>)}</select></label>
      {/* Model stays free text: OpenAI-compatible endpoints expose different model IDs and there is no discovery contract. */}
      <label>Model<input aria-label="Model" value={model} placeholder="Model ID" onChange={event => setModel(event.target.value)} /></label>
    </div>
    {hint && <p className="composerHint">{hint}</p>}
    <textarea aria-label="Message" placeholder="Continue the conversation…" value={content} onChange={event => setContent(event.target.value)} />
    <button disabled={disabled || !provider || !model.trim() || !content.trim()}>{disabled ? "Generating…" : "Send"}</button>
  </form>;
}
