# The game starts again (the symbiote HUD crashed it on launch)

- The symbiote HUD (`WebSymbioteHud`) made its three order icons (shield, sword, ender eye) as the class loaded, during the client's start, before items are ready ("Components not bound yet"), so every client crashed on launch. The icons are now made when drawn.
- Built by CI; checked against the crash report; not played.
