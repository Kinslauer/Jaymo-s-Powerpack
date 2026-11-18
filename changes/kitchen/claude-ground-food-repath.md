# Animals stop re-pathing to food they cannot reach

- Fixes: an animal by dropped food it couldn't reach (behind a fence, across water) worked out a new path to it every tick for ten seconds, a large share of the server's freezes on fresh land. It now tries once a second, and leaves the food when there is no way to it (`EatGroundFoodGoal`).
- Built by CI; not played.
