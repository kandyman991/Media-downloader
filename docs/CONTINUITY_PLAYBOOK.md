# Continuity playbook — StreamCatch / Media Downloader

**On resume:** Read `AGENTS.md`, `CHAT_HANDOFF.md`, `README.md` and relevant durable notes. Check current GitHub `main`, open PRs, CI status, and release asset SHA when an APK is involved.

**On milestone completion:**
- Record exact commit/PR, test or CI run link, and platform/device outcome.
- Separate implementation merged to main from an experimental branch and from planned work.
- Update the next concrete task and blockers in `CHAT_HANDOFF.md`.
- Add durable architecture decisions to `ENGINEERING_DECISIONS.md`, and experiment failures or regression traps to `FAILED_APPROACHES.md`.
- Avoid expensive builds and GitHub Actions artifact uploads just to refresh documentation.

There is **no automatic state.json refresher installed by this documentation package**. Live GitHub is authoritative; do not fabricate CI/build state from chat memory. Do not commit secrets. ChatGPT does not autonomously copy chats to the repository.
