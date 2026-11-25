# NaN rotation diagnostic
- The Deck server spammed "Invalid entity rotation: NaN, discarding." with no stack trace. A mixin on `Entity.setYRot` / `setXRot` now logs one warning with the entity (type, id, name, position, dimension), the bad value, which setter, and a stack trace, at most once per entity type and 5 traces per run. Vanilla still discards the value; nothing else changes.
- `NanRotationDiagnosticMixin`, registered in `jaymospowers.mixins.json`.
- Not built locally (CI builds it); not played.
