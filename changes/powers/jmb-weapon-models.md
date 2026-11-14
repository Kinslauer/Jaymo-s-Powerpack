# JMB's 3D models for the longsword, glaive, quarterstaff, cudgel and battleaxe; blood on 3D weapons
- Andy picked five of JMB's Better Weapons' 3D models (MIT, jmb19905; licence kept in the pack and `docs/licenses`). In the hand they are now solid: the **longsword**, the **glaive** (every tier), the **quarterstaff** as JMB's battlestaff (wood and iron, swinging across into JMB's blocking hold while raised), the **cudgel** as JMB's club (wood, and a stone one in cobblestone), and WeaponMod's **battleaxe** as JMB's long axe (every metal). The inventory icons stay as they were.
- Stone and copper versions are JMB's iron palette recoloured along vanilla's stone and copper sword heads.
- They live in the built-in "3D items and plants" pack (`tools/gen_held_3d.py`, sources in `tools/jmb_weapons/`), so switching that pack off brings the flat look back.
- **Blood on 3D weapons**: a bloody modelled weapon (these, and the dagger) now shows its blood in the hand, on the ground and in frames, painted onto each texture it's drawn from (`BloodItemOverlay`, `ArmourGrimeTextures.sprite`). Before, only flat weapons did.
- Compiles; not played.
