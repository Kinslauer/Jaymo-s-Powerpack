# offline accounts sign in before making their character
- **Sign-in screen**: on a server running EasyAuth, a player on an offline (non-premium) account gets a sign-in screen
  when they join: the password kept with their name, or a new one typed twice the first time. It sends EasyAuth's own
  login or register command, so nobody types a slash command, and it comes down once they're in. Typed passwords show
  as dots; Leave quits the server. Premium players never see it (EasyAuth checks them with Mojang and lets them in).
- **Character creation waits**: a newcomer's life story, MCA's creator, heritage and class screens open only once they've
  signed in (EasyAuth drops a client's mod packets until then, so the screens could never be answered and the player was
  stuck until kicked). Story stasis leaves a player who hasn't signed in alone too.
- Classes: SignIn (EasyAuth read by reflection, only when it's loaded), client/SignInScreen, mixin/mca/McaJoinDestinyMixin
  (MCA's first-join creator held back), Classes and StoryStasis gated, McaCompat/McaBridge.launchDestiny. Lang part
  sign-in.json. Without EasyAuth nothing changes.
- Built; not played (needs the Deck server with EasyAuth and an offline account to try).
