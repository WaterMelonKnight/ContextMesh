export type SourceType = "IMPORTED_CONVERSATION" | "NATIVE_CONVERSATION";
export type Role = "SYSTEM" | "USER" | "ASSISTANT" | "TOOL";
export type ContentPart = { type: "TEXT"; text: string };
export type Message = { id: string; sequenceNo: number; role: Role; content: ContentPart[] };
export type Origin = { sourceConversationId: string; throughMessageId: string | null };
export type ConversationSummary = { id: string; sourceType: SourceType; sourceProvider: string | null; title: string | null; createdAt: string; updatedAt: string; origin: Origin | null };
export type Conversation = ConversationSummary & { messages: Message[] };
