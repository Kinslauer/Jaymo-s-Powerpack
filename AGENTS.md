# Jaymo's Powers, Kingdoms and Kitchen

- Andy's words for things, and their names in the code, are in `docs/vault/Context/Glossary.md`.
- Find code through `docs/codemap/<powers|kingdoms|kitchen>.md` (one line per class, rebuilt at every release) before grepping.
- Not Claude Code? Before editing, open every `.claude/rules/*.md` whose `paths:` match the files you'll touch.
- Build to check your work. Don't start dev client or server runs unless Andy asks.
- No version numbers in code comments (`(0.21.62)`): say what the code does. Leave the old ones as they are.

## Check, don't assume (every task)
1. Prove "done" with a check (build, grep, test, codemap), then go through the ask line by line against what you
   built. No part of an ask is left out, cut down or swapped for something easier without saying so.
2. Never invent a class, method, file, number, mod, PR or ruling you haven't seen. Say "unknown" instead.
3. Never say something is missing until you've searched every likely name (codemap, grep, Decisions.md, task notes).
4. Say what you couldn't check.
