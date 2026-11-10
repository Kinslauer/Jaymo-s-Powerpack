# Powerpack Builder (Claude Managed Agent)

A Claude agent that does a Powerpack task in Anthropic's cloud instead of on Andy's PC: it clones the repo into its
own sandbox, builds with JDK 25, runs `tools/pr_checks.py`, pushes a `cloud/<task>` branch and opens a PR. A separate
grader checks the work against a rubric (in `run.py`) and sends it back up to 3 times. YOUTH (Hermes) starts it with
`run.py`; it is billed to Andy's Anthropic API key (Claude Opus 5.5 at $4 / $20 per million tokens in / out, plus
$0.08 an hour of sandbox time), with a cap of $20 a session (`BUDGET_CENTS` in `run.py`).

| File | What it is |
|---|---|
| `agents/powerpack-builder.md` | The agent: model, tools, and its instructions (the Markdown body) |
| `environments/powerpack-build.yaml` | Its sandbox: only the hosts the mods' builds download from |
| `vaults/powerpack-github.yaml` | The vault that holds the GitHub token for opening PRs |
| `run.py` | Starts a session on a task, streams it, and keeps the task's Inbox status |
| `claude-lock.json` | Written by `ant apply`: the IDs of the three above. Commit it |

## One-time setup (on Andy's PC)

1. **Keys**, as user environment variables (Windows: Settings, System, About, Advanced system settings, Environment
   Variables), never in a file:
   - `ANTHROPIC_API_KEY`: from platform.claude.com, API keys.
   - `GITHUB_TOKEN`: a fine-grained personal access token (github.com, Settings, Developer settings) for
     `kinslauer/jaymo-s-powerpack` only, with **Contents: Read and write**, **Pull requests: Read and write**,
     **Metadata: Read**. It clones and pushes (through Anthropic's git proxy, never inside the sandbox) and opens PRs.
2. **Tools**: `pip install anthropic`, and the `ant` CLI 1.34.0 or newer (`ant --version`; install it from the Anthropic
   CLI docs on platform.claude.com), then `ant auth login` or rely on `ANTHROPIC_API_KEY`.
3. **Create the agent, environment and vault**, from this folder:
   ```
   cd tools/cloud-agent
   ant apply --dry-run -v agents/powerpack-builder.md environments/powerpack-build.yaml vaults/powerpack-github.yaml
   ant apply agents/powerpack-builder.md environments/powerpack-build.yaml vaults/powerpack-github.yaml
   ```
   The first prints what will be made; the second asks, then makes it and writes `claude-lock.json`. Commit that file
   (in a PR), or the next `ant apply` makes duplicates. After editing any of the three files, run the second command
   again: it updates them in place.
4. **Put the GitHub token in the vault**: `py tools/cloud-agent/run.py credentials` (from the repo root).
5. **Test it** on something small: `py tools/cloud-agent/run.py prompt "Fix a typo in docs/HANDOFF.md and open a PR"`.
   It prints a Console link where the whole session can be watched live.

## Running tasks

```
py tools/cloud-agent/run.py task <inbox-task>      # e.g. run.py task ore-hearts
py tools/cloud-agent/run.py prompt "<what to do>"
py tools/cloud-agent/run.py next                   # the oldest open task for `cloud` or `prometheus`
```

`next` shares Prometheus's tasks: whichever claims a task first does it (exit 3: nothing waiting). `task` and
`next` never take a task someone else has claimed.

`task` claims the Inbox task as `claude-cloud`, gives the agent the task's text, and afterwards sets it `in-review`
with the PR number, `blocked` with the agent's question for Andy, or back to `open` if it ended without a PR. The rest
of the pipeline (Quill's text pass, the reviewer, Andy) is unchanged. Exit codes: 0 PR, 2 needs Andy, 1 otherwise.

While it runs, the agent may only open a PR on this repository: `run.py` refuses every other call that asks for
permission. It never merges.

### For YOUTH

Add to YOUTH's instructions (Hermes):

> When Andy asks for work to be done in the cloud, run `py tools/cloud-agent/run.py task <task-name>` from the
> Jaymo's Powerpack checkout (for a request that isn't an Inbox task: `run.py prompt "<the request, with all its
> detail>"`). When no cloud run is going and Andy has said the cloud may pick up work, run `run.py next`: it takes the
> oldest open task for `cloud` or `prometheus` (exit 3: nothing waiting). It can take an hour; post Andy the Console link it
> prints. When it ends, tell Andy the PR number (exit 0), the agent's question (exit 2), or that it stopped without a PR
> (exit 1), with the session link. One cloud run at a time. Never put a key in a command line or a file.

## Bug hunter (on demand only)

A second agent, `agents/bug-hunter.md` (Andy's definition, 2026-11-09), in its own sandbox `environments/bug-hunt.yaml`
(GitHub only, nothing installed). It reads an area of the code with many agents at once (a workflow run: readers, a
second look at every slice, then one prover per suspicion), and reports only bugs a separate agent proved or confirmed
by reading. Each confirmed bug becomes an Inbox task `for: hephaestus`, with the failing test and the suggested fix;
Hephaestus (hermes-fixer) takes those when nothing is waiting in review fixes, and they go through review as usual.

```
py tools/cloud-agent/run.py hunt Downed --dry-run     # what it would read: files and lines, no cost
py tools/cloud-agent/run.py hunt Downed               # a word from docs/codemap, a folder, or a .java file
py tools/cloud-agent/run.py hunt kitchen/src --big --budget 120
```

Run it only when Andy asks. Cost grows with the lines read: one ordinary run is up to 30,000 lines (20 slices of
~1,500), capped at $50 a session (`HUNT_BUDGET_CENTS`). A whole mod is far bigger (Powers ~380,000 lines, Kingdoms
~340,000, Kitchen ~48,000), so hunt one area at a time; `--big` lifts the line cap, and needs Andy's OK and a higher
`--budget`. The report and `bugs.json` are saved in `tools/cloud-agent/hunts/` (not committed). Exit 0 with the tasks
made (or none), 1 if the hunt didn't finish.

Setup, once, from this folder: `ant apply agents/bug-hunter.md environments/bug-hunt.yaml` and commit
`claude-lock.json`.

## Changing it

- Its instructions: the body of `agents/powerpack-builder.md`, then `ant apply` again.
- What "done" means: `RUBRIC` in `run.py` (a starter: tune it as the grader's notes show what it misses).
- Cost: `BUDGET_CENTS` in `run.py`. A session that reaches it pauses; raising its budget in the Console resumes it.
- A build host it can't reach: add it to `environments/powerpack-build.yaml` and `ant apply`.
