# loose wood no longer crashes the server while land generates
- **Fix**: the server no longer crashes when trees come down while new land is generating. Block changes made by world generation threads are now queued (up to 4096 at a time) and looked at on the server thread the next tick, instead of touching the loose-wood lists from another thread.
- Class: `LooseWood`.
- Built by CI; not played.
