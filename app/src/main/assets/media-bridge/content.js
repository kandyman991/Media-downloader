"use strict";
// No page-injected JavaScript and no privileged page messaging.
const MEDIA = /\.(m3u8|mpd|mp4|m4v|webm|mov)(?:[?#]|$)/i;
const seen = new Set();
function scan() {
  const found = [];
  function add(value) {
    if (typeof value !== "string" || !value.startsWith("https://") || !MEDIA.test(value)) return;
    if (seen.has(value) || seen.size >= 1000) return;
    seen.add(value);
    found.push(value);
  }
  for (const el of document.querySelectorAll("video, video source, source")) {
    add(el.currentSrc);
    add(el.src);
  }
  for (const resource of performance.getEntriesByType("resource")) add(resource.name);
  if (found.length) browser.runtime.sendMessage({ type: "media", urls: found.slice(0, 250) }).catch(() => {});
}
let scheduled = false;
function scheduleScan() {
  if (scheduled) return;
  scheduled = true;
  setTimeout(() => { scheduled = false; scan(); }, 450);
}
document.addEventListener("play", scheduleScan, true);
new MutationObserver(scheduleScan).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["src"] });
browser.runtime.onMessage.addListener(message => { if (message?.type === "scan") scan(); });
scan();
