# AI bundle

Everything another AI assistant needs to carry on this project, besides the code. Start with `docs/AI-HANDBOOK.md`.

- `memory/`: the Claude project memory exported on 2026-11-06, one file per topic (features, decisions, gotchas,
  Andy's answers). `memory/INDEX.md` lists every file with a one-line description; grep it to find the topic you need.
  `[[name]]` inside a file links to `memory/name.md`. Internal thread ids were removed. Files describe what was true
  when written, so check the code before relying on a file path or class name.
- `references/Reference-Notes.md`: one short entry for every mod page, web page or video Andy shared (licence,
  loader, what we took, verdict, PR). Grep it before reading a link again, and add an entry after reading a new one.
- `readout/modpack-readout.md`: a full readout of the modpack's mods and features (as of 2026-10-26).

Skills, agents and workflows are not copied here; they stay in `.claude/` so there is one copy:
`.claude/skills/pbdr/SKILL.md`, `.claude/agents/`, `.claude/workflows/audit-fix.js`.

## Using it with another assistant

- Codex and most agent tools read `AGENTS.md`; Gemini CLI reads `GEMINI.md`. Both files at the repo root point to
  `CLAUDE.md` and the handbook.
- Cursor: add a rule in `.cursor/rules/` that says "Follow CLAUDE.md and docs/AI-HANDBOOK.md", or paste them in.
- A chat-only model: paste `CLAUDE.md`, `docs/AI-HANDBOOK.md` and the memory files for the topic at hand.
- Skills are just markdown instructions: paste a skill's text into the other tool's rules or prompt when you want it.
