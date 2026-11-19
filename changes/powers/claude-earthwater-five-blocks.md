# Earth & Water's chunk scan skipped where it can find nothing

- Fixes: the earlier speed-up for Earth & Water's per-chunk scan checked for any of its blocks, and its common ones are in most chunks, so the scan still ran almost everywhere (216 samples of a 37 s freeze on a new world). It now looks only for the five blocks the scan ticks.
- Built by CI; not played.
