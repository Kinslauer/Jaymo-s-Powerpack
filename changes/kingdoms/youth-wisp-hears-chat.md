# Youth hears questions through proximity chat
- Proximity chat never sends a line to the log in vanilla's `<name> text` form, and players show as nicknames
  ("Stranger"), so Hermes's listener never saw anyone call Youth. Now every chat line that starts with "youth" (any case,
  with or without a comma: "youth, how does Crush work?", "youth how does boxing work", "!youth what is ki") also goes in
  the server log as `[youth-ask] <account name> <question>`, the name `/youth summon` and `/youth say` take. Lines that
  only mention Youth somewhere else are left out.
- Right-clicking the wisp gives one hint per click (it was one per hand), at most every 5 seconds.
- Built; not seen in game.
