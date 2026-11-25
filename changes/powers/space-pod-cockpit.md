# space pod: sit in the cockpit under a glass canopy, new icon and flight picture
- **In game** (Andy: "you should be sitting in that cockpit, and there needs to be a glass enclosure over it"): the pod
  is Alex's Caves' submarine lying as he built it, its thrusters out on their arms instead of stood on end, drawn a
  little bigger (0.7) so a seated pilot fits. The pilot sits in its seat, facing the bow (they can look about 105
  degrees round), and a second seat sits beside them. The levers moved out to either side of the pilot's legs.
- **Glass canopy**: our own stepped glass bubble in slate frames with a riveted rib, hinged at the top of the hull
  behind the pilot (`PodCanopy`, `tools/gen_pod_canopy.py`). It tilts up 50 degrees when someone gets in or out and
  shuts after (`PodEntity.canopyOpen`). A damaged canopy is drawn cracked.
- **Climbing in** uses the horse mount clips: a foot up and a leg swung over from whichever side you stand (from behind,
  over its left side), and off the way you get off a horse (`SeatSteps`).
- **Item icon**: a flat 16px side view of the pod in the vanilla style rules (vanilla's copper ramp, slate motor and
  periscope, glass with the pilot's head in it) instead of the old capsule block model. Set down, the item now stands
  up as the pod itself at once, facing the way you look.
- **Flight screen**: the pod is drawn side on (`textures/gui/space_pod_flight.png`), its fire coming out of its motor.
  Both pictures come from `tools/vanilla_style/space_pod.py`.
- Without Alex's Caves, our own pod model and seat are unchanged.
- Built; not played. The seat height and canopy fit were checked in preview renders of the model, not in game.
