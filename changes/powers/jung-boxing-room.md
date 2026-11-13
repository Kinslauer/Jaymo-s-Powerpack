# Boxing: room between the gloves and the body, and the Long Guard's legs

- (Jung, from his playtest clips) The three styles' gloves sat inside the face and chest: the Arrow Slit's gloves
  covered the face, the Long Guard's and the Bastion's rear glove sank into the cheek, the Bastion's lead forearm into
  the belly. Every guard and block is re-set so the worn gear clears the head, body and other arm (checked on a box
  model of the player with the gear's real size, through the idle sway).
- Arrow Slit: elbows lower and forward, gloves up in front of the chin and mouth instead of on the face. Its block
  brings them together in front of the chin.
- Long Guard: the rear glove beside the jaw. Its block holds both arms out a little lower, bent, hands apart.
- Bastion: the lead forearm out in front of the belly rather than in it; the rear glove beside the jaw; its block
  turns no further side on than its stance, the lead shoulder raised less.
- A cross brings the lead glove home to the Arrow Slit's guard spot.
- Long Guard legs: the feet stand about under the hips, toes straight ahead, the knees soft; they were turned out
  with the body and spread.
- (Jung, in-game tuning) The Arrow Slit's gloves sit a little higher (they may brush the chin); the Bastion's lead
  forearm is tucked in closer across the body; the Long Guard's thighs are a little wider apart.
- Arrow Slit's string is now jab, lead hook, cross, and round again (it was a one-two, then alternating hooks).
- (Jung: "the body looks disconnected") The body now leans and turns about the hips, not the neck: every crouch,
  lean, side lean and turn is added up and put on the model last (`BoxingPoses.settle`), with the waist kept on the
  legs and the head and shoulders riding the top of the trunk. No more gap at the waist when the body leans, and no
  head floating off the shoulders in a slip.
- Punches lean the trunk in over the hit and sit a little into the knees: more for a cross, a hook also tilting over
  the leg on its side, the uppercut dipping into the knees before it rises.
- Evades are a step in and a duck, the body together: a slip leans off the line about the hips and goes a little
  forward as the knees bend; a bob folds forward and sinks; the pull shifts the weight back over the rear leg and
  sits into the knees; a shoulder roll dips in and away. The gloves stay at the face through them.
- (Jung: "bastion looks ruined, make it look like the video with overlapping") The Bastion's arms are back in front
  of the body as in the Philly shell footage, a hair off it: the lead forearm folded across the belly, the rear glove
  in front of the jaw. Its shoulders roll forward round the chest (`BASTION_LEAD_ROLL`, `BASTION_REAR_ROLL`) so the
  arms reach there past a body turned side on. Its block raises the lead forearm across the ribs.
- The guard turns with the body when a punch or a shoulder roll turns it, so the hand that stays home no longer goes
  into the face; the punching arm turns with the body until it fires. A straight's windup coils the body, not the
  glove back into the chin. The Arrow Slit's gloves are a touch apart so they don't sink into the face as it leans in.
  Gassed hands hang a little further forward, off the belly.
