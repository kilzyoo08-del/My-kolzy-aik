import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { readJsonObject, boundedString } from "./request_guard.js";

const encoder = new TextEncoder();

function requestFromChunks(chunks, headers = {}) {
  let index = 0;
  const body = new ReadableStream({
    pull(controller) {
      if (index < chunks.length) controller.enqueue(chunks[index++]);
      else controller.close();
    }
  });
  return new Request("https://example.test/functions/v1/centerion", {
    method: "POST",
    headers,
    body,
    duplex: "half"
  });
}

async function responseStatus(promise) {
  try { await promise; }
  catch (error) {
    assert.ok(error instanceof Response);
    return error.status;
  }
  assert.fail("Expected request guard to reject input");
}

test("accepts a JSON object at the exact UTF-8 byte limit", async () => {
  const raw = '{"я":1}';
  const bytes = encoder.encode(raw);
  const request = requestFromChunks([bytes], { "content-length": String(bytes.byteLength) });
  assert.deepEqual(await readJsonObject(request, bytes.byteLength), { "я": 1 });
});

test("rejects a chunked body that exceeds the byte limit before full buffering", async () => {
  const raw = encoder.encode('{"value":"too long"}');
  const chunks = [raw.slice(0, 8), raw.slice(8)];
  assert.equal(await responseStatus(readJsonObject(requestFromChunks(chunks), 10)), 413);
});

test("rejects a declared oversized body immediately", async () => {
  const request = new Request("https://example.test", {
    method: "POST",
    headers: { "content-length": "100" },
    body: "{}"
  });
  assert.equal(await responseStatus(readJsonObject(request, 10)), 413);
});

test("rejects a content-length mismatch", async () => {
  const request = new Request("https://example.test", {
    method: "POST",
    headers: { "content-length": "1" },
    body: "{}"
  });
  assert.equal(await responseStatus(readJsonObject(request, 10)), 400);
});

test("rejects malformed JSON, arrays, and invalid UTF-8", async () => {
  assert.equal(await responseStatus(readJsonObject(requestFromChunks([encoder.encode("{")]), 10)), 400);
  assert.equal(await responseStatus(readJsonObject(requestFromChunks([encoder.encode("[]")]), 10)), 400);
  assert.equal(await responseStatus(readJsonObject(requestFromChunks([new Uint8Array([0xff])]), 10)), 400);
});

test("boundedString rejects rather than truncates and counts Unicode code points", () => {
  assert.equal(boundedString("  ok  ", 2), "ok");
  assert.equal(boundedString("😀😀", 2), "😀😀");
  assert.throws(() => boundedString("abc", 2), error => error instanceof Response && error.status === 400);
  assert.equal(boundedString(null, 10), "");
});

test("Centerion still derives the owner from verified Supabase Auth, never request body", async () => {
  const source = await readFile(new URL("../centerion/index.ts", import.meta.url), "utf8");
  const authPosition = source.indexOf("await userClient.auth.getUser()");
  const ownerPosition = source.indexOf("const userId = auth.user.id");
  const serviceRolePosition = source.indexOf("createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY");
  assert.ok(authPosition >= 0 && ownerPosition > authPosition && serviceRolePosition > ownerPosition);
  assert.doesNotMatch(source, /body\.(owner_id|user_id)/);
  assert.match(source, /readJsonObject\(req, MAX_BODY\)/);
});
