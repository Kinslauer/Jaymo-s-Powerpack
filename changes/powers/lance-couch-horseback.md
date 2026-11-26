# lance couching, horseback only
- **Couch the lance**: right-click with an Armory lance while riding a horse you steer and it is couched for 1.5 s, like Kingdoms' Couched Lance form (no form needed). The first foe it meets is hit for your attack x0.6 up to x4 by the horse's speed, thrown back, and knocked out of its saddle (it and anyone riding it get Slowness). 3 s wait between couches; a hit wears the lance 1.
- **Horseback only**: horses, donkeys, mules, zebras and similar; never a pig, camel, llama, goat, elephant or dragon. The lance's faster-you-ride swing bonus now also needs a horse, and doesn't stack onto a couched blow.
- On foot, right-click still raises the guard; on horseback it couches instead.
- New `armory/LanceCouch`; `Armory.charge` checks `LanceCouch.horseback`. No couch pose: the rider's arm isn't redrawn.
- Built; not played.
