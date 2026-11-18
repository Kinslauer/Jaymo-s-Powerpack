# Blood decals draw under Iris shaders

- Changes: blood puddles, footprints and wall marks are registered with Iris (as translucent particles) when it is loaded. Before, switching a shader pack on made Iris log "Missing program minecraft:pipeline/jaymospowers_blood_decal in override list" and a burst of GL_INVALID_OPERATION errors, and the decals could fail to draw.
- Built by CI; not played.
