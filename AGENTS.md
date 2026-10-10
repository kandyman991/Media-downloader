# StreamCatch / Media Downloader — ChatGPT development operating contract

1. Read `CHAT_HANDOFF.md`, `README.md`, the decision/failure notes and `docs/CONTINUITY_PLAYBOOK.md` at each new session.
2. Verify **live** main SHA, open PRs, CI runs and release details via GitHub. Never treat a Markdown file as proof of today's status.
3. Continue the handoff's next exact action, validating the existing code and user-confirmed device behavior before changing functionality.
4. Respect the project constraints recorded in `docs/ENGINEERING_DECISIONS.md`; do not revive rejected methods without a tested reason.
5. Finish each milestone by updating the handoff with tested commit, PR/run URLs, observed success/failure and the next exact task; update decision and failure records as necessary.
6. Do not use GitHub Actions artifact uploads for distribution. Avoid expensive CI builds for documentation-only changes; use `[skip ci]` when appropriate.
7. Never commit passwords, SSH keys, tokens, signing keys, private IP/host identifiers, or sensitive user data.

**Scope:** ChatGPT can retrieve repository files on demand, but this file does not grant background execution and does not make chats automatically persist in Git.
