# Fall Hall cutter

How `kingdoms/src/main/resources/data/jaymoskingdoms/structure/fall_hall/manor.nbt` was made from PaleoTech's Fall Hall map
(CurseForge file 9313294; credits and changes in `kingdoms/src/main/resources/FALL-HALL-CREDITS.md`).
Needs Python 3 with `nbtlib numpy scipy pillow`.

    # 1. the map's blocks round the manor (unzip the map first)
    python3 cut3d.py "<map folder>" area 2130 -64 -300 2420 200 -40
    # 2. which of them were built (not grown)
    python3 built.py area
    # 3. the manor alone, emptied, as a 26.3 structure file
    python3 cut_template.py area manor.nbt

The manor's box, its main floor and the margin round its walls are constants at the top of `cut_template.py`.
