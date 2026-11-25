# Mordhau harness 1: helms - fifteen new helms, the kettle in each realm's cut, the barbute re-cut
- **New helms** (Andy's Mordhau harness picks; our own cubes of the historical helms, nothing of Mordhau's), each with
  every finish, dents and an icon: the Broad-Brimmed and the Tall Kettle Hat, the Deep Kettle Hat (its pulled-down
  front with two sights lifts like a visor), the Kettle Sallet (wear a bevor with it for the bevored look), the Archer's
  Sallet, the Long-Tailed Sallet (its front with one sight lifts), the Gallowglass Helm, the Visored Barbute, the
  Visored Great Helm, the Flat-Topped Helm, the Nasal Helm, the Spired Helm, the Spectacle Helm (fixed face), the
  Skullcap (it sits over a mail coif) and the Turban Helm. Open-faced helms show the face; visored ones show it with
  the visor up.
- **The kettle hat comes in each realm's cut** (Andy: "different cuts for different realms"): the realms that arm their
  guards in plate wear the tall cut, the tabard realms the round kettle hat, the half-plate realms the broad brim
  (`HeraldryWear.KETTLE_CUTS`). A fiefdom wears its overlord's cut; a village or haven has its own by its arms. It
  goes for the soldiers who wore the kettle hat and for knights whose realm's own helm is the kettle.
- **The barbute is re-cut**: its T-opening is open now, so the face shows through it.
- Classes: `HeraldryArmour` (the fifteen helms; not in `HELMS`, so no realm's own helm changes), `HeraldryWear`
  (`kettle`), `GuardKit` (the kettle cut), `kingdoms/tools/gen_heraldry_armour.py` (the models, `shell` for open
  faces), the plate, heavy and head armour tags.
- Built by CI; not played. Not seen in game: how the new helms sit on MCA bodies, the visors lifting, and crests on the
  pointed helms (their crest heights are guesses).
