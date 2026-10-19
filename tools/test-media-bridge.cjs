"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const callbacks = {};
const delivered = [];
const port = {
  postMessage: m => delivered.push(m),
  onMessage: { addListener: cb => { callbacks.portMessage = cb; } },
  onDisconnect: { addListener() {} }
};
const browser = {
  runtime: {
    connectNative: name => {
      assert.equal(name, "streamcatch");
      return port;
    },
    onMessage: { addListener: cb => { callbacks.message = cb; } }
  },
  webRequest: {
    onBeforeRequest: { addListener: cb => { callbacks.beforeRequest = cb; } },
    onBeforeSendHeaders: { addListener: (cb, filter, extra) => {
      assert.deepEqual(Array.from(extra), ["requestHeaders"]);
      callbacks.beforeSendHeaders = cb;
    } }
  },
  tabs: { query: async () => [] }
};
const source = fs.readFileSync("app/src/main/assets/media-bridge/background.js", "utf8");
vm.runInNewContext(source, { browser, URL, setTimeout() {} }, { timeout: 2000 });
const stream = "https://cdn.example.org/video.m3u8?token=opaque";
callbacks.beforeRequest({ method: "GET", tabId: 1, url: stream });
assert.equal(delivered.at(-1).referrerOrigin, "");
callbacks.beforeSendHeaders({
  method: "GET", tabId: 1, url: stream,
  requestHeaders: [
    { name: "Referer", value: "https://player.example.org/embed/movie?secret=do-not-send" },
    { name: "Cookie", value: "session=do-not-send" },
    { name: "Authorization", value: "Bearer do-not-send" }
  ]
});
assert.equal(delivered.at(-1).source, "Browser headers");
assert.equal(delivered.at(-1).referrerOrigin, "https://player.example.org");
assert(!JSON.stringify(delivered.at(-1)).includes("do-not-send"));
callbacks.beforeSendHeaders({ method: "GET", tabId: 1, url: stream, requestHeaders: [] });
assert.equal(delivered.at(-1).source, "Browser headers");
assert.equal(delivered.at(-1).referrerOrigin, "");
callbacks.beforeSendHeaders({
  method: "GET", tabId: 1, url: stream,
  requestHeaders: [{ name: "Referer", value: "https://user:pass@untrusted.example/" }]
});
assert.equal(delivered.at(-1).referrerOrigin, "");
const before = delivered.length;
callbacks.beforeRequest({ method: "GET", tabId: -1, url: stream });
callbacks.beforeRequest({ method: "GET", tabId: 1, url: "https://example.org/photo.jpeg" });
assert.equal(delivered.length, before);
callbacks.message({
  type: "media",
  urls: [stream],
  frameOrigin: "https://iframe.example.net/player?foo=bar"
});
assert.equal(delivered.at(-1).referrerOrigin, "https://iframe.example.net");
console.log("Media bridge request-origin and token-redaction tests passed");
