# letterbox cutscene and POV swap on NPC dialogue
- Starting a talk with an MCA villager opens a letterbox cutscene: black bars slide in, the HUD clears, and the camera swaps between the two of you.
- Every time the speaker changes (player <-> villager), the POV swaps with it: behind the player's shoulder looking at the villager while the player speaks, at the villager's eyes looking at the player while they speak. It ends when the talk screen closes.
- New server -> client payload `jaymoskingdoms:dialogue_cue` (`DialogueCue`) carries whose turn it is; the client's `DialogueCinematic` draws the bars, and the camera chain (`HandshakeCameraMixin`) applies the view each frame.
- `sh ./gradlew build` compiles; not played in game.
