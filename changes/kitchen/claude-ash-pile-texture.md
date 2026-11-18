# ash pile texture fixed
- **Ash piles**: `ash_pile.png` had a broken zlib stream (no header) and an RGB colour type over RGBA data, so every client showed the missing texture. Re-encoded as a valid 16x16 RGBA PNG with the same pixels. The eight ash pile models now also set a `particle` texture.
- Scanned every PNG under the three mods: this was the only malformed one.
- Not built or played.
