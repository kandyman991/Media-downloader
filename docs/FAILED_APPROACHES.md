# Failed or Disallowed Approaches — StreamCatch / Media Downloader

Preserve hard-earned failures and guardrails. The entries below include both observed regressions and security/scope prohibitions; **do not treat an untested prohibition as an observed test failure**.

## 1. Known risk/regression

Ephemeral GitHub runner debug-signing certificates caused Android app-not-installed failures on upgrades. Preserve stable signing and verify it rather than reintroducing rotating certificates.

Only reconsider with an explicit hypothesis, code/issue link and regression evidence.

## 2. Guardrail

Do not save unverified MP4 data as a successful conversion; use the supported TS fallback.

Only reconsider with an explicit hypothesis, code/issue link and regression evidence.

## 3. Guardrail

DRM bypass, decryption of protected streams and downloads without permission are not part of the project scope.

Only reconsider with an explicit hypothesis, code/issue link and regression evidence.

