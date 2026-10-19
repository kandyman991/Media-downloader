"use strict";
// Observe media URL and the *already-used* browser referrer origin, if any.
// Never transmit Cookie, Authorization, Referer paths/queries, or signed tokens as metadata.
const MEDIA = /\.(m3u8|mpd|mp4|m4v|webm|mov)(?:[?#]|$)/i;
let nativePort = null;
function safeOrigin(value) {
  if (typeof value !== "string" || value.length > 8192) return "";
  try {
    const u = new URL(value);
    if (u.protocol !== "https:" || u.username || u.password) return "";
    return u.origin;
  } catch (_) { return ""; }
}
function deliver(url, source, observedReferrer) {
  if (typeof url !== "string" || url.length > 8192 || !url.startsWith("https://") || !MEDIA.test(url)) return;
  try {
    nativePort?.postMessage({ type: "media", url, source, referrerOrigin: safeOrigin(observedReferrer) });
  } catch (_) {}
}
function connect() {
  try {
    const port = browser.runtime.connectNative("streamcatch");
    nativePort = port;
    port.onMessage.addListener(message => {
      if (message?.type === "scan") {
        browser.tabs.query({ active: true }).then(tabs => {
          for (const tab of tabs) {
            browser.tabs.sendMessage(tab.id, { type: "scan" }).catch(() => {});
          }
        }).catch(() => {});
      }
    });
    port.onDisconnect.addListener(() => {
      if (nativePort === port) nativePort = null;
      setTimeout(connect, 1200);
    });
  } catch (_) {
    setTimeout(connect, 1200);
  }
}
browser.webRequest.onBeforeRequest.addListener(
  details => {
    if (details.method === "GET" && details.tabId >= 0) deliver(details.url, "Network", "");
  },
  { urls: ["https://*/*"] }
);
// Read only Referer from a request that Gecko actually made.
// We do not invent a player origin and do not retain arbitrary request headers.
browser.webRequest.onBeforeSendHeaders.addListener(
  details => {
    if (details.method !== "GET" || details.tabId < 0) return;
    const referer = (details.requestHeaders || []).find(
      item => item.name.toLowerCase() === "referer"
    );
    if (referer && typeof referer.value === "string") {
      deliver(details.url, "Browser headers", referer.value);
    }
  },
  { urls: ["https://*/*"] },
  ["requestHeaders"]
);
browser.runtime.onMessage.addListener(message => {
  if (message?.type === "media" && Array.isArray(message.urls)) {
    for (const url of message.urls.slice(0, 250)) {
      deliver(url, "Page frame", message.frameOrigin);
    }
  }
});
connect();
