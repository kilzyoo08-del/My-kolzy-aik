/**
 * Bounded JSON request parsing for the Centerion Supabase Edge Function.
 * No secrets, network calls, or external dependencies.
 */

/**
 * Read a JSON object with a strict UTF-8 byte limit before buffering it fully.
 * @param {Request} request
 * @param {number} maxBytes
 * @returns {Promise<Record<string, any>>}
 */
export async function readJsonObject(request, maxBytes = 32_000) {
  if (!Number.isSafeInteger(maxBytes) || maxBytes < 1) {
    throw new TypeError("maxBytes must be a positive safe integer");
  }

  const contentLength = request.headers.get("content-length");
  let declaredLength = null;
  if (contentLength !== null) {
    if (!/^\d+$/.test(contentLength)) {
      throw new Response("invalid_content_length", { status: 400 });
    }
    declaredLength = Number(contentLength);
    if (!Number.isSafeInteger(declaredLength)) {
      throw new Response("invalid_content_length", { status: 400 });
    }
    if (declaredLength > maxBytes) {
      throw new Response("payload_too_large", { status: 413 });
    }
  }

  const reader = request.body?.getReader();
  if (!reader) throw new Response("invalid_json", { status: 400 });

  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!(value instanceof Uint8Array)) {
        throw new Response("invalid_request_body", { status: 400 });
      }
      totalBytes += value.byteLength;
      if (totalBytes > maxBytes) {
        try { await reader.cancel(); } catch { /* best-effort cancellation */ }
        throw new Response("payload_too_large", { status: 413 });
      }
      chunks.push(value);
    }
  } catch (error) {
    if (error instanceof Response) throw error;
    throw new Response("invalid_request_body", { status: 400 });
  } finally {
    try { reader.releaseLock(); } catch { /* reader may already be released */ }
  }

  if (declaredLength !== null && declaredLength !== totalBytes) {
    throw new Response("content_length_mismatch", { status: 400 });
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }

  let raw;
  try {
    raw = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    throw new Response("invalid_utf8", { status: 400 });
  }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Response("invalid_json", { status: 400 });
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Response("json_object_required", { status: 400 });
  }
  return parsed;
}

/**
 * Validate rather than silently truncate user-provided strings.
 * The limit counts Unicode code points, not UTF-16 code units.
 * @param {unknown} value
 * @param {number} maxChars
 * @returns {string}
 */
export function boundedString(value, maxChars) {
  if (!Number.isSafeInteger(maxChars) || maxChars < 0) {
    throw new TypeError("maxChars must be a non-negative safe integer");
  }
  if (typeof value !== "string") return "";
  const normalized = value.trim();
  if (Array.from(normalized).length > maxChars) {
    throw new Response("field_too_long", { status: 400 });
  }
  return normalized;
}
