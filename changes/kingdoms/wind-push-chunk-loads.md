# The wind's push no longer loads land
- The wind no longer loads chunks on the server thread to see whether something is under cover, and only moves mobs and items where the world is ticking: two 22 s stalls while land was being generated, with nobody on (`WindPush`: the shelter ray counts as cover where a chunk isn't loaded; entities outside entity-ticking range are skipped).
- Built by CI; not played.
