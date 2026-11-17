# pb-barrage (Kingdoms)
- pb-punch heavy impact: Spirit Punch's ring and dust wave now start at the target's feet (position + 0.1) instead of body centre, with the impact tuning (1.5F ring, 0xFFFFFF, 5.0 wave, 0xD0D0D0, 10 ticks) as static final constants.
- pb-barrage clash: while two flurries meet, blurred fists paint in front of each side and sparks fly between them (Fx.stroke added to Kingdoms' Fx door to Powers; SpiritBarrage imports Fx again).
- Built: sh ./gradlew build in kingdoms/ BUILD SUCCESSFUL; python tools/pr_checks.py passed. Not tested in game.
