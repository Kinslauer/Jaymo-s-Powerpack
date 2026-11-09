# Aetherion: an editable model and GeckoLib animations
- Aetherion keeps its star-dragon body, four hides, glowing markings, horns, spines, saddle, rider seats and hatchling scale, now drawn from an editable Blockbench model instead of Java-built parts.
- Idle, walking, wingbeats, gliding, landing, sitting, crashing, biting, Cataclysm and tail sweeps are keyed animations; its head still follows its look and its jaw opens with the starlight beam.
- Original project geometry and keyframes from `kingdoms/tools/gen_aetherion_geo.py`; the body-plan reference supplied by Andy was public-domain/CC0. No Saint's Dragons art or animation is used.
- Geometry matched against all 88 original cubes; front/side/back and eleven old/new pose renders inspected. Kingdoms clean build and root build pass. A dedicated server boot was attempted, but concurrent Gradle activity deleted shared build output before startup; not tested in a client.
