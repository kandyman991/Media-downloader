"use strict";
// Bundled bridge: observations only. Never blocks network traffic or handles cookies.
const MEDIA = /\.(m3u8|mpd|mp4|m4v|webm|mov)(?:[?#]|$)/i;
let nativePort = null;
function deliver(url, source) {
  if (typeof url !== "string" || url.length > 8192 || !url.startsWith("https://") || !MEDIA.test(url)) return;
  try { nativePort?.postMessage({ type: "media", url, source }); } catch (_) {}
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
  details => { if (details.method === "GET") deliver(details.url, "Network"); },
  { urls: ["https://*/*"] }
);
browser.runtime.onMessage.addListener(message => {
  if (message?.type === "media" && Array.isArray(message.urls)) {
    for (const url of message.urls.slice(0, 250)) deliver(url, "Page");
  }
});
connect();
