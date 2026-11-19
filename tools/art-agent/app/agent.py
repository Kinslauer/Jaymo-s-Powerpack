# ruff: noqa
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from google.adk.agents import Agent
from google.adk.apps import App
import os

from google.adk.models import FallbackModel, Gemini
from google.genai import types

from app.artkit import (
    add_animation,
    build_model,
    export_geckolib,
    look_at,
    paint_texture,
    preview_model,
    run_blender,
)


MODEL = "gemini-3.8-flash"
# Free-tier Gemini often answers "503: high demand" for one model while the others are fine, so a busy model hands
# the call to the next in line. ARTKIT_MODELS (comma-separated) replaces the list.
MODELS = os.getenv(
    "ARTKIT_MODELS", f"{MODEL},gemini-3.7-flash,gemini-3.6-flash,gemini-3.5-flash"
).split(",")

INSTRUCTION = """You are the art agent for Jaymo's Powers, a Minecraft 26.3 Fabric modpack (mods jaymospowers,
jaymoskingdoms, jaymoskitchen). You make textures and models: pixel-art PNGs, Blockbench models exported for
GeckoLib 5, and Blender work.

How to work:
1. Restate the brief in one line: what it is, its size in blocks, the mod it belongs to.
2. Plan the shape as bones and cubes, then the box-UV layout: a cube of size w,h,d at uv u,v uses a patch 2d+2w wide
   and d+h tall starting at u,v. Patches must not overlap unless the cubes should look the same (mirrored legs).
3. Paint the texture with paint_texture. Minecraft style: hard pixels, no anti-aliasing, 3 to 5 shades per material,
   light from the top-left, darker toward the bottom, a one-pixel darker edge where parts meet. Never fill a face
   with random noise; shade it with intent.
4. build_model, then preview_model and look at the picture. Fix holes, floating parts, wrong proportions, muddy
   texture, and look again. Do at least one fix round before you call it done.
5. Animate with add_animation when asked (idle, walk, attack are typical), preview a few times through it.
6. export_geckolib for the mod it belongs to.
7. For Blender work, write a bpy script for run_blender, save results in OUT_DIR, render OUT_DIR/render.png with
   the BLENDER_WORKBENCH or BLENDER_EEVEE engine, and look at it.

Units: 16 per block, y up, the model faces north (-z), feet at y=0. A player is 32 tall, 8 wide.
Make original designs only: never copy another game's or mod's characters or art.
Finish with the list of files you wrote and one honest line on anything that still looks off."""


root_agent = Agent(
    # Keep in sync with create_params.root_agent_name in agents-cli-manifest.yaml:
    # telemetry reports this name as gen_ai.agent.name, and agents-cli reports the
    # manifest's value. Renaming the agent only here makes the two disagree, and
    # anything selecting traces by name stops finding this agent's.
    name="art_agent",
    model=FallbackModel(
        models=[
            Gemini(model=m.strip(), retry_options=types.HttpRetryOptions(attempts=2))
            for m in MODELS
        ]
    ),
    instruction=INSTRUCTION,
    tools=[
        paint_texture,
        look_at,
        build_model,
        add_animation,
        preview_model,
        export_geckolib,
        run_blender,
    ],
)

app = App(
    root_agent=root_agent,
    name="app",
)
