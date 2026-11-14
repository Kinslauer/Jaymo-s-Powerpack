# The game no longer crashes on dying (Skitter's ceiling camera)
- **Fix (client crash)**: the Xenoform Skitter's ceiling-walk camera (`SkitterCeiling`) asked the camera's body for its
  number every frame, and the new body a death gives has none for a frame or two: "Tried to access entity ID before ID
  assignment" closed Andy's game twice on dying (2026-11-13 20:34 and 20:44). A body with no number yet counts as not
  turned over.
- Built; tested by dying on the Deck server.
