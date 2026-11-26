# lance couching from the saddle
- **Couch the lance**: right-click with an Armory lance while riding and it is couched for 1.5 s, like Kingdoms' Couched Lance form (no form needed). The first foe it meets is hit for your attack x0.6 up to x4 by the mount's speed, thrown back, and knocked out of its saddle (it and anyone riding it get Slowness). 3 s wait between couches; a hit wears the lance 1.
- **Only where the lance reaches**: horses, donkeys, mules, pigs, goats, llamas and the like. Not from anything taller than 2 blocks (camels, moas) or any camel, elephant, mammoth, dragon or wyvern; a rider up there is told the lance can't reach. The lance's faster-you-ride swing bonus follows the same rule, and doesn't stack onto a couched blow.
- On foot, right-click still raises the guard; mounted, it couches instead.
- New `armory/LanceCouch` (`horseback`, `MAX_MOUNT_HEIGHT`); `Armory.charge` checks it. No couch pose: the rider's arm isn't redrawn.
- Built; not played.
