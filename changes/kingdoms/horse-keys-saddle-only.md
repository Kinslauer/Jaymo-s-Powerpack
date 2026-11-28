# horse keys only in the saddle
- **Icy's Better Horses' riding keys wait for the saddle**: Rear Horse (default H, the character menu) and Toggle Horse
  Charge (default B, the fourth ability key) do nothing unless you are steering a horse, and Open Cart (default Left Alt,
  the roll) does nothing unless you are riding. On foot, Rear used to rear the horse under your crosshair, the charge key
  flipped the setting and printed "charge on/off", and Open Cart opened the cart you looked at (sneak and right-click
  the cart still opens it). The other action on a shared key fires as before: the game hands each press to every
  binding on that key. In the saddle the three keys work as they did. Icy's gear keys and free look already waited for
  the saddle; its whistle, command wheel and Manage Horses keys are for a horse you are not on, so they still work on
  foot. (`client/mixin/icyhorses/IcySaddleOnlyKeysMixin`, a mixin into Icy's own client class, skipped without Icy's.)
- **Free Rein Camera's toggle** only switches in the saddle (it flipped the setting with a message on foot).
  (`FreeReinClient`)
- Kingdoms' other mount keys already did nothing on foot: Horse Trick, Strike your captive, Dragon Breath, Dragon Bite;
  so did Powers' dinosaur Special and Roar.
- Built; not played.
