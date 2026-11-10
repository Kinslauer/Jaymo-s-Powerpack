# F5 goes between first person and behind; the front camera moves to F6

Tiv: "F5 should only toggle between first and 3rd person, the front facing camera should be toggled on w/ f6."
- **F5** now goes between first person and the view from behind (Shoulder Surfing's over-the-shoulder view in the pack). It never lands on the view from the front. From the front view, F5 goes to first person.
- **F6** turns the camera round to look at your face. Press it again and you're back in the view you had before (first person or from behind). If the game doesn't know what that was, you go to the view from behind. It's in Options > Controls > Key Binds under Miscellaneous ("Toggle Front Camera"), next to Toggle Perspective, and can be rebound there.
- **Cutscenes**: while an ultimate's film, a beam battle's film, Time Flow's shoulder view or a finisher seen from behind has the camera, F6 does nothing. Each of these gives back the view it found when it ends, so the front view comes back after them and F6 still takes you back to the view you had before it.
- **Nothing else moved**: nothing in the pack was on F6. First-person Model's toggle is meant to start on F6, but on 26.3 it starts on a key code no key sends. Vanilla only uses F6 as F3+F6, which doesn't trigger key binds. So `KeyConflicts` is unchanged.
- `FrontCamera` (the key and the toggle), `mixin/FrontCameraCycleMixin` (vanilla's F5), `mixin/shouldersurfing/ShoulderSurfingFrontMixin` (Shoulder Surfing's F5; its "third person front" setting stays on so F6 still works), `ChronoFlowClient#holdsView`, `FinisherPose#holdsView`. Shoulder Surfing Reloaded is now a compile-only dependency. Lang: `lang/parts/camera.json`.
- Built; not played.
