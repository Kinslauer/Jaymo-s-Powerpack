# fix the server crash when a rat finishes raiding food
- A rat that finished eating or stealing from a food store cleared its target, but its raid could tick once more before
  the game noticed it was over, and that tick crashed the server ("Ticking entity", `Rat$RaidGoal.tick`). The raid now
  waits out that tick instead. Built; not played.
