export type SourceType = "IMPORTED_CONVERSATION" | "NATIVE_CONVERSATION";
export type Role = "SYSTEM" | "USER" | "ASSISTANT" | "TOOL";
export type ContentPart = { type: "TEXT"; text: string };
export type Message = { id: string; sequenceNo: number; role: Role; content: ContentPart[] };
export type Origin = { sourceConversationId: string; throughMessageId: string | null };
export type ConversationSummary = { id: string; sourceType: SourceType; sourceProvider: string | null; title: string | null; createdAt: string; updatedAt: string; origin: Origin | null };
export type Conversation = ConversationSummary & { messages: Message[] };
/** Mirrors the read-only provider status API; it never carries credentials or endpoint URLs. */
export type ProviderKind = "BUILT_IN" | "EXTERNAL";
export type Provider = { id: string; displayName: string; kind: ProviderKind; defaultModel: string | null };
