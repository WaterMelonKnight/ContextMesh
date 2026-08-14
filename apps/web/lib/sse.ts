export type SseEvent = { event: string; data: unknown };
export async function consumeSse(response: Response, receive: (event: SseEvent) => void) {
  if (!response.ok || !response.body) throw new Error(await responseError(response));
  const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, "\n");
    let boundary: number;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const frame = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
      let event = "message"; const data: string[] = [];
      for (const line of frame.split("\n")) { if (line.startsWith("event:")) event = line.slice(6).trim(); if (line.startsWith("data:")) data.push(line.slice(5).trimStart()); }
      if (data.length) { const raw = data.join("\n"); try { receive({ event, data: JSON.parse(raw) as unknown }); } catch { receive({ event, data: raw }); } }
    }
    if (done) break;
  }
}
export async function responseError(response: Response) { try { const p = await response.json() as { detail?: string; title?: string }; return p.detail ?? p.title ?? `Request failed (${response.status})`; } catch { return `Request failed (${response.status})`; } }
