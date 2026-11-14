import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.0";
import { boundedString as safeString, readJsonObject } from "../_shared/request_guard.js";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const API_VERSION = "1.2.0";
const KOLAI_URL = Deno.env.get("CENTERION_KOLAI_URL") ?? "";
const KOLAI_TOKEN = Deno.env.get("CENTERION_KOLAI_TOKEN") ?? "";
function kolaiBaseUrl() { let u = KOLAI_URL.trim(); while (u.endsWith("/")) u = u.slice(0, -1); return u; }
const MAX_BODY = 32_000;
const MAX_MESSAGE = 12_000;

function corsHeaders(req: Request) {
  const origin = req.headers.get("origin");
  const allowed = (Deno.env.get("CENTERION_ALLOWED_ORIGINS") ?? "")
    .split(",").map((x) => x.trim()).filter(Boolean);
  const headers: Record<string, string> = {
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-request-id",
    "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
    "Vary": "Origin",
  };
  if (origin && allowed.includes(origin)) headers["Access-Control-Allow-Origin"] = origin;
  return headers;
}
function json(req: Request, data: unknown, status = 200, requestId = crypto.randomUUID()) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders(req), "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", "X-Request-Id": requestId },
  });
}
function error(req: Request, code: string, status: number, requestId: string) {
  return json(req, { error: { code, message: code, request_id: requestId } }, status, requestId);
}
async function readBody(req: Request) {
  return await readJsonObject(req, MAX_BODY);
}
function routePath(url: URL) {
  return url.pathname.replace(/^.*\/functions\/v1\/centerion/, "") || "/";
}


Deno.serve(async (req: Request) => {
  const requestId = req.headers.get("x-request-id") || crypto.randomUUID();
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders(req) });

  const path = routePath(new URL(req.url));
  if (req.method === "GET" && (path === "/health" || path === "/v1/health")) {
    let modelLoaded = false;
    if (KOLAI_URL && KOLAI_TOKEN) {
      try {
        const upstreamHealth = await fetch(kolaiBaseUrl() + "/v1/health", {
          signal: AbortSignal.timeout(4000),
        });
        if (upstreamHealth.ok) {
          const state = await upstreamHealth.json();
          modelLoaded = state.status === "ok" && state.model_loaded === true;
        }
      } catch { modelLoaded = false; }
    }
    return json(req, {
      status: modelLoaded ? "ok" : "degraded",
      api_version: API_VERSION,
      model_loaded: modelLoaded,
      memory_ready: true,
      reason: modelLoaded ? "kolai_upstream_ready" : "language_core_not_connected",
    }, 200, requestId);
  }

  const authHeader = req.headers.get("authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) return error(req, "unauthorized", 401, requestId);

  try {
    const userClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: auth, error: authErr } = await userClient.auth.getUser();
    if (authErr || !auth.user) return error(req, "unauthorized", 401, requestId);
    const userId = auth.user.id;

    const db = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    if (req.method === "GET" && path === "/v1/kernels") {
      if (!KOLAI_URL || !KOLAI_TOKEN) return error(req, "model_unavailable", 503, requestId);
      try {
        const upstream = await fetch(kolaiBaseUrl() + "/v1/kernels", {
          headers: { "Authorization": "Bearer " + KOLAI_TOKEN },
          signal: AbortSignal.timeout(8000),
        });
        if (!upstream.ok) return error(req, "model_unavailable", 503, requestId);
        return json(req, await upstream.json(), 200, requestId);
      } catch { return error(req, "model_unavailable", 503, requestId); }
    }

    const kernelMatch = path.match(/^\/v1\/kernels\/([a-zA-Z0-9_]+)$/);
    if (req.method === "POST" && kernelMatch) {
      if (!KOLAI_URL || !KOLAI_TOKEN) return error(req, "model_unavailable", 503, requestId);
      const body = await readBody(req);
      const query = safeString(body.query, MAX_MESSAGE);
      const context = body.context && typeof body.context === "object" && !Array.isArray(body.context)
        ? body.context : {};
      try {
        const upstream = await fetch(kolaiBaseUrl() + "/v1/kernels/" + kernelMatch[1], {
          method: "POST",
          headers: { "Authorization": "Bearer " + KOLAI_TOKEN, "Content-Type": "application/json" },
          body: JSON.stringify({ query, context }),
          signal: AbortSignal.timeout(15000),
        });
        if (upstream.status === 403) return error(req, "approval_required", 403, requestId);
        if (upstream.status === 404) return error(req, "not_found", 404, requestId);
        if (!upstream.ok) return error(req, "model_unavailable", 503, requestId);
        return json(req, await upstream.json(), 200, requestId);
      } catch { return error(req, "model_unavailable", 503, requestId); }
    }

    if (req.method === "GET" && path === "/v1/conversations") {
      const limit = Math.min(100, Math.max(1, Number(new URL(req.url).searchParams.get("limit") ?? 20)));
      const { data, error: e } = await db.from("centerion_conversations")
        .select("id,title,model,created_at,updated_at")
        .eq("user_id", userId).order("updated_at", { ascending: false }).limit(limit);
      if (e) throw e;
      return json(req, { conversations: data ?? [], request_id: requestId }, 200, requestId);
    }

    const messagesMatch = path.match(/^\/v1\/conversations\/([0-9a-f-]+)\/messages$/i);
    const conversationMatch = path.match(/^\/v1\/conversations\/([0-9a-f-]+)$/i);
    if (req.method === "GET" && (messagesMatch || conversationMatch)) {
      const id = (messagesMatch ?? conversationMatch)![1];
      const { data: conv, error: ce } = await db.from("centerion_conversations")
        .select("id,title,model,created_at,updated_at")
        .eq("id", id).eq("user_id", userId).maybeSingle();
      if (ce) throw ce;
      if (!conv) return error(req, "not_found", 404, requestId);
      if (conversationMatch) return json(req, { conversation: conv, request_id: requestId }, 200, requestId);
      const { data, error: me } = await db.from("centerion_messages")
        .select("id,role,content,metadata,created_at")
        .eq("conversation_id", id).eq("user_id", userId)
        .order("created_at", { ascending: true }).limit(500);
      if (me) throw me;
      return json(req, { conversation: conv, messages: data ?? [], request_id: requestId }, 200, requestId);
    }

    if (req.method === "GET" && path === "/v1/settings") {
      const { data, error: e } = await db.from("centerion_user_settings")
        .select("preferences,updated_at").eq("user_id", userId).maybeSingle();
      if (e) throw e;
      return json(req, { settings: data ?? { preferences: {} }, request_id: requestId }, 200, requestId);
    }
    if (req.method === "PATCH" && path === "/v1/settings") {
      const body = await readBody(req);
      if (!body.preferences || typeof body.preferences !== "object" || Array.isArray(body.preferences)) {
        return error(req, "invalid_request", 400, requestId);
      }
      const { data, error: e } = await db.from("centerion_user_settings")
        .upsert({ user_id: userId, preferences: body.preferences, updated_at: new Date().toISOString() }, { onConflict: "user_id" })
        .select("preferences,updated_at").single();
      if (e) throw e;
      return json(req, { settings: data, request_id: requestId }, 200, requestId);
    }

    if (req.method === "GET" && path === "/v1/memory") {
      const url = new URL(req.url);
      const query = safeString(url.searchParams.get("q"), 500);
      const limit = Math.min(8, Math.max(1, Number(url.searchParams.get("limit") ?? 5)));
      if (!query) return error(req, "query_required", 400, requestId);
      const { data, error: e } = await db.from("centerion_memory_documents")
        .select("id,title,source_path,content,metadata,updated_at")
        .eq("owner_id", userId)
        .textSearch("search_vector", query, { type: "websearch", config: "simple" })
        .limit(limit);
      if (e) throw e;
      return json(req, { memories: data ?? [], request_id: requestId }, 200, requestId);
    }
    if (req.method === "POST" && path === "/v1/memory") {
      const body = await readBody(req);
      const title = safeString(body.title, 200);
      const sourcePath = safeString(body.source_path, 500);
      const content = safeString(body.content, 12_000);
      if (!title || !sourcePath || !content) return error(req, "invalid_request", 400, requestId);
      const { data, error: e } = await db.from("centerion_memory_documents")
        .upsert({
          owner_id: userId, title, source_path: sourcePath, content,
          metadata: body.metadata && typeof body.metadata === "object" && !Array.isArray(body.metadata) ? body.metadata : {},
          embedding: null, updated_at: new Date().toISOString(),
        }, { onConflict: "owner_id,source_path" })
        .select("id,title,source_path,updated_at").single();
      if (e) throw e;
      return json(req, { memory: data, request_id: requestId }, 201, requestId);
    }
    const memoryMatch = path.match(/^\/v1\/memory\/([0-9a-f-]+)$/i);
    if (req.method === "DELETE" && memoryMatch) {
      const { data, error: e } = await db.from("centerion_memory_documents")
        .delete().eq("id", memoryMatch[1]).eq("owner_id", userId).select("id").maybeSingle();
      if (e) throw e;
      if (!data) return error(req, "not_found", 404, requestId);
      return json(req, { deleted: true, request_id: requestId }, 200, requestId);
    }

    if (req.method === "POST" && path === "/v1/feedback") {
      const body = await readBody(req);
      const rating = Number(body.rating);
      if (!Number.isInteger(rating) || rating < 1 || rating > 5) return error(req, "invalid_request", 400, requestId);
      const messageId = safeString(body.message_id, 64) || null;
      const conversationId = safeString(body.conversation_id, 64) || null;
      if (messageId) {
        const { data, error: e } = await db.from("centerion_messages").select("id,conversation_id")
          .eq("id", messageId).eq("user_id", userId).maybeSingle();
        if (e) throw e;
        if (!data || (conversationId && data.conversation_id !== conversationId)) return error(req, "not_found", 404, requestId);
      }
      if (conversationId) {
        const { data, error: e } = await db.from("centerion_conversations").select("id")
          .eq("id", conversationId).eq("user_id", userId).maybeSingle();
        if (e) throw e;
        if (!data) return error(req, "not_found", 404, requestId);
      }
      const { data, error: e } = await db.from("centerion_feedback").insert({
        user_id: userId, message_id: messageId, conversation_id: conversationId, rating,
        category: safeString(body.category, 80) || null,
        comment: safeString(body.comment, 2000) || null,
      }).select("id,created_at").single();
      if (e) throw e;
      return json(req, { feedback: data, request_id: requestId }, 201, requestId);
    }

    if (req.method === "POST" && path === "/v1/chat") {
      const body = await readBody(req);
      const message = safeString(body.message, MAX_MESSAGE);
      if (!message) return error(req, "invalid_request", 400, requestId);
      if (!KOLAI_URL || !KOLAI_TOKEN) return error(req, "model_unavailable", 503, requestId);

      const conversationId = safeString(body.conversation_id, 64);
      if (conversationId && !/^[0-9a-f-]{36}$/i.test(conversationId)) {
        return error(req, "invalid_request", 400, requestId);
      }
      if (conversationId) {
        const { data: existing, error: lookupError } = await db.from("centerion_conversations")
          .select("id").eq("id", conversationId).eq("user_id", userId).maybeSingle();
        if (lookupError) throw lookupError;
        if (!existing) return error(req, "not_found", 404, requestId);
      }

      let memoryRows: Array<{id:string; title:string; content:string}> = [];
      if (body.options?.use_memory !== false) {
        const { data: memories, error: memoryError } = await db.from("centerion_memory_documents")
          .select("id,title,content").eq("owner_id", userId)
          .textSearch("search_vector", message.slice(0, 500), { type: "websearch", config: "simple" })
          .limit(5);
        if (memoryError) throw memoryError;
        memoryRows = memories ?? [];
      }
      const nl = String.fromCharCode(10);
      const memoryContext = memoryRows.map((m) => "[" + m.title + "]" + nl + m.content).join(nl + "---" + nl).slice(0, 4000);

      let upstream: Response;
      try {
        upstream = await fetch(kolaiBaseUrl() + "/v1/chat", {
          method: "POST",
          headers: { "Authorization": "Bearer " + KOLAI_TOKEN, "Content-Type": "application/json" },
          body: JSON.stringify({
            message, conversation_id: conversationId || null, request_id: requestId,
            context: { memory_context: memoryContext },
          }),
          signal: AbortSignal.timeout(30000),
        });
      } catch {
        return error(req, "model_unavailable", 503, requestId);
      }
      if (!upstream.ok) return error(req, "model_unavailable", 503, requestId);
      let generated: any;
      try { generated = await upstream.json(); }
      catch { return error(req, "model_unavailable", 503, requestId); }
      const answer = safeString(generated.answer, MAX_MESSAGE);
      if (!answer) return error(req, "model_unavailable", 503, requestId);

      let savedConversationId = conversationId;
      if (!savedConversationId) {
        const { data: created, error: createError } = await db.from("centerion_conversations")
          .insert({ user_id: userId, title: message.slice(0, 80), model: "kolai_3m_v1" })
          .select("id").single();
        if (createError) throw createError;
        savedConversationId = created.id;
      }

      const toolSummary = Array.isArray(generated.tool_summary) ? generated.tool_summary : [];
      const verificationStatus = safeString(generated.verification_status, 40) || "unverified";
      const { data: savedMessages, error: saveError } = await db.from("centerion_messages").insert([
        { conversation_id: savedConversationId, user_id: userId, role: "user", content: message,
          metadata: { request_id: requestId } },
        { conversation_id: savedConversationId, user_id: userId, role: "assistant", content: answer,
          metadata: { request_id: requestId, model: generated.model ?? { name: "centerion", version: "kolai_3m_v1" },
            verification_status: verificationStatus, tool_summary: toolSummary } },
      ]).select("id,role");
      if (saveError) throw saveError;
      const assistantMessage = (savedMessages ?? []).find((m: any) => m.role === "assistant");
      await db.from("centerion_conversations").update({
        updated_at: new Date().toISOString(), model: "kolai_3m_v1",
      }).eq("id", savedConversationId).eq("user_id", userId);

      return json(req, {
        conversation_id: savedConversationId,
        message_id: assistantMessage?.id ?? null,
        answer,
        model: generated.model ?? { name: "centerion", version: "kolai_3m_v1" },
        memory_sources: memoryRows.map((m) => ({ id: m.id, title: m.title })),
        tool_summary: toolSummary,
        verification_status: verificationStatus,
        request_id: requestId,
        finish_reason: "stop",
      }, 200, requestId);
    }

    return error(req, "not_found", 404, requestId);
  } catch (e) {
    if (e instanceof Response) {
      const code = e.status === 413 ? "payload_too_large" : "invalid_request";
      return error(req, code, e.status, requestId);
    }
    console.error("Centerion API failure", { request_id: requestId, message: e instanceof Error ? e.message : "unknown" });
    return error(req, "internal_error", 500, requestId);
  }
});