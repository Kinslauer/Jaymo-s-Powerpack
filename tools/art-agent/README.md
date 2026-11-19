# art-agent

Andy's first Google agents CLI agent (ADK Python, Gemini): it makes the pack's art. It paints pixel-art PNGs,
builds Blockbench models (.bbmodel, GeckoLib animated-entity format) and animates them, draws previews the way the
game does and looks at them to fix what's off, exports GeckoLib 5 files laid out like a mod's assets, and runs
Blender headless for anything Blockbench can't do.

| Tool | What it does |
|---|---|
| `paint_texture` | a PNG from a palette and rows of characters, one per pixel |
| `look_at` | shows the agent any PNG (texture, preview, render, reference) |
| `build_model` | bones and box-UV cubes into `out/models/<name>.bbmodel` (opens in Blockbench) |
| `add_animation` | keyframed animations on that model |
| `preview_model` | five-turn preview sheet via `tools/bbmodel_bake.py` + `tools/bbmodel_preview.py` |
| `export_geckolib` | `geo.json`, `animation.json` and texture via `tools/bbmodel_geckolib.py` into `out/assets/<mod>/` |
| `run_blender` | a bpy script in headless Blender; files and `render.png` land in `out/blender/<name>/` |

Code: `app/agent.py` (the agent and its instructions), `app/artkit.py` (the tools), `app/cli.py` (the tools without Gemini).
Everything it writes goes to `out/` (git-ignored); copy what you keep into the mod's resources.

## As Prometheus's artist

Prometheus (the `hermes-modeller` bot) hands its art to this agent; its scheduled prompt is in
`docs/vault/Pipeline.md`. Without Gemini, the same tools run from the command line:

```sh
python -m app.cli tools                       # the tools and their arguments
python -m app.cli paint_texture args.json     # any tool, arguments from a JSON file (or a JSON string)
python -m app.cli install <name> <mod>        # copy the exported files into the mod's resources (--overwrite to replace)
```

## On Andy's PC (once, for Prometheus)

1. Install uv: `powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"`, then open a new terminal.
2. `uv tool install "google-agents-cli~=1.15.0"`, then in `tools/art-agent`: `agents-cli install`.
3. The Gemini key as a Windows user environment variable `GEMINI_API_KEY` (Settings, System, About, Advanced system
   settings, Environment Variables). No `.env` needed: with the key set, the agent uses Google AI Studio.
4. For Blender work: install Blender and set `BLENDER_BIN` to its `blender.exe`.
5. Check: `agents-cli run "Paint a 16x16 item texture of a copper key"`.

## In a cloud thread

Needs `GEMINI_API_KEY` as a plain environment variable in the cloud environment (never a network secret: the proxy's
Bearer header breaks Google API keys).

```sh
cd tools/art-agent && sh setup_cloud.sh      # agents CLI, packages, Blender 5.3 + software OpenGL, .env
agents-cli run "Paint a 16x16 item texture of a copper key with a green patina"
agents-cli playground                         # chat with it in a browser (local machine)
```

---


Simple ReAct agent
Agent generated with `agents-cli` version `1.15.0`

## Project Structure

```
art-agent/
├── app/         # Core agent code
│   ├── agent.py               # Main agent logic
│   ├── fast_api_app.py        # FastAPI Backend server
│   └── app_utils/             # App utilities and helpers
├── tests/                     # Unit, integration, and load tests
├── GEMINI.md                  # AI-assisted development guide
└── pyproject.toml             # Project dependencies
```

> 💡 **Tip:** Use [Antigravity CLI](https://antigravity.google/) for AI-assisted development - project context is pre-configured in `GEMINI.md`.

## Requirements

Before you begin, ensure you have:
- **uv**: Python package manager (used for all dependency management in this project) - [Install](https://docs.astral.sh/uv/getting-started/installation/) ([add packages](https://docs.astral.sh/uv/concepts/dependencies/) with `uv add <package>`)
- **agents-cli**: Agents CLI - Install with `uv tool install google-agents-cli`
- **Google Cloud SDK**: For GCP services - [Install](https://cloud.google.com/sdk/docs/install)


## Quick Start

Install `agents-cli` and its skills if not already installed:

```bash
uvx google-agents-cli setup
```

Install required packages:

```bash
agents-cli install
```

Test the agent with a local web server:

```bash
agents-cli playground
```

You can also use features from the [ADK](https://adk.dev/) CLI with `uv run adk`.

## Commands

| Command              | Description                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------- |
| `agents-cli install` | Install dependencies using uv                                                         |
| `agents-cli playground` | Launch local development environment                                                  |
| `agents-cli lint`    | Run code quality checks                                                               |
| `agents-cli eval`    | Evaluate agent behavior (generate, grade, analyze, and more — see `agents-cli eval --help`) |
| `uv run pytest tests/unit tests/integration` | Run unit and integration tests                                                        || [A2A Inspector](https://github.com/a2aproject/a2a-inspector) | Launch A2A Protocol Inspector                                                        |

## 🛠️ Project Management

| Command | What It Does |
|---------|--------------|
| `agents-cli scaffold enhance` | Add CI/CD pipelines and Terraform infrastructure |
| `agents-cli infra cicd` | One-command setup of entire CI/CD pipeline + infrastructure |
| `agents-cli scaffold upgrade` | Auto-upgrade to latest version while preserving customizations |

---

## Development

Edit your agent logic in `app/agent.py` and test with `agents-cli playground` - it auto-reloads on save.

## Deployment

```bash
gcloud config set project <your-project-id>
agents-cli deploy
```

To add CI/CD and Terraform, run `agents-cli scaffold enhance`.
To set up your production infrastructure, run `agents-cli infra cicd`.

## Observability

Built-in telemetry exports to Cloud Trace, BigQuery, and Cloud Logging.

## A2A Inspector

This agent supports the [A2A Protocol](https://a2a-protocol.org/). Use the [A2A Inspector](https://github.com/a2aproject/a2a-inspector) to test interoperability.
See the [A2A Inspector docs](https://github.com/a2aproject/a2a-inspector) for details.
