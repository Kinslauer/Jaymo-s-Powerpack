# Dynamic Waters: no more chunks without terrain from crossing streams
- When two mountain streams' mouths landed on each other, Dynamic Waters' check for streams left dry by ravines asked each one about the other forever. The worker thread ran out of stack and the chunk was left without terrain (seen during a new world's pregen). A stream met again in the same check now counts as not left dry.
