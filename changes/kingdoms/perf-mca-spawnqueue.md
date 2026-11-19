# MCA's village swap rests while its land isn't loaded
- **Performance**: While Chunky makes land with nobody near, every new village's villagers wait in MCA's swap queue for their land to load. The check (`McaSpawnQueueMixin`) no longer counts the whole queue each tick, and after a look that finds none ready it waits a few ticks before looking again. None is lost. Once a village's land loads, its swap can come a few ticks later than before.
- Moose: Cube's moose type (`Moose.cowType`) is looked up once instead of on every entity load.
- Built by CI; not played.
