---
name: jaki-ships
description: Jaki Versatile Structures Sails & Sea (GPLv3) ships used as crewed ships in Kingdoms (JakiShips), PR #349 MERGED as Kingdoms 0.7.112
metadata:
  type: project
  modified: 2026-10-21T02:52:06.791Z
---
2026-10-21 Andy asked to use Jaki's ships to replace our templates and for pirates or other factions. The licence is GPLv3, so 5 .nbt files ship unchanged in kingdoms data/jaymoskingdoms/structure/jaki (with licenses/jaki).
- Kingdoms `sailing/JakiShips` converts each file at launch: adds a helm, turns blackstone guns into cannons, adds rail guns, ties loose sails on with chain rigging, puts sails in the crew's colour.
- Who sails what: pirates galleon (was brigantine) or sometimes drakkar; navies war trireme (was brigantine); merchants cargo ship or trader (was cog).
- Wool in the rigging counts as sail (3 blocks = 1). Ship.MOST_BLOCKS is now 5000. The op command `/kingdom fleet ship <rig>` builds any rig to look at.
- PR #349 (branch claude/project-thread-qubnvs) MERGED 2026-10-21 03:0xZ. Not playtested with crews or a client.
Related: [[sailing]].
