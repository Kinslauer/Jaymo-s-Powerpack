# symbiote - a takeover no longer freezes a host who isn't a web slinger, and a blob that left you runs away
- **The freeze:** the symbiote's own side of the client (a takeover steering you, its countdown, the struggle bar
  and dark edges, wall crawling, the tendril tether) only ran for web slingers. In anyone else it took over (say when
  a settlement saw it come over you), the takeover locked your keys and then did nothing: you stood frozen, with no
  bar to fight it, until the server gave up. Now it runs for every host: it drives you, you see the bar and can tear
  free, and the red mark shows on what the tendrils would grab.
- **A blob that won't take you runs:** when a symbiote leaves someone alive, the blob it becomes flees from them at
  full speed until it's 40 blocks off and makes its home there, and it runs again whenever they come within 16
  blocks (unless they've attacked it: then it fights). It used to sit where it left you. Who it has left is now saved
  with it, so it still won't take them back after a reload.
- `WebPowerClient.hostTick` / `hostHud` (called from `JaymosPowersClient` and `PowerHud`), `WebSymbioteBlob`
  (`spurn` flees, `shun`, `SHUN_RANGE`, saved `spurned`).
- Built; not played.
