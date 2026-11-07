# Rules

The rules live in the repo; this note only points at them. Paths are from the repo root (agents open them directly;
in Obsidian use the GitHub links, since Obsidian only opens files inside the vault).

- **`CLAUDE.md`**: the short project rules every agent follows.
  [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/CLAUDE.md)
- **`docs/AI-HANDBOOK.md`**: the long version. Worth knowing by section:
  - 4. Versions, branches, PRs, merging, and the **before-you-open-a-PR checklist** (also run
    `python3 tools/pr_checks.py`; the PR checks Action re-checks it).
  - 6. How Andy likes to work. 7. **Andy's standing rulings** (the rulings from before [[Decisions]] started).
  - 8. Agents, verification, the Gemini second opinion. 9. Open issues.
  [GitHub](https://github.com/Kinslauer/Jaymo-s-Powerpack/blob/master/docs/AI-HANDBOOK.md)
- **`docs/ai-bundle/`**: exported project memory, references and the modpack readout (`README.md` explains).
- **`.claude/agents/`** (`scanner`, `editor`), **`.claude/skills/pbdr/`** (pull, build, deploy, restart the Steam
  Deck server), **`.claude/workflows/audit-fix.js`**.

The ones that bite most often:
- Andy merges only when Andy says "merge". Never merge yourself; one PR at a time.
- Bump the version of every mod you touched, to the next number no open PR has taken, right before the PR.
- Never commit `Serverfiles/` or `playit-secret.txt`.
- Check every Minecraft API against the 26.3 sources (`./gradlew genSources`).
- Build to check; don't start long dev client/server runs unless Andy asks.
