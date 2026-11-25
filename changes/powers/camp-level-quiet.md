# No Subtle Effects error on a dedicated server's start

- Turning off Subtle Effects' skeleton bone particles (`SubtleEffectsCompat`) now runs only in a game's own server. On a dedicated server Subtle Effects' config class needs client classes, so it failed with a long `LocalPlayer` error in the log at every start. Each client still turns the bones off itself when it joins.
- Built by CI; not played.
