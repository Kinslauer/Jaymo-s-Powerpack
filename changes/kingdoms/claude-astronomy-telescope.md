# Articulated GeckoLib telescope

The telescope block now animates smoothly when aimed. Its mount turns and its barrel tilts to track the sky, ready for the Altazimuth Mount skill. One telescope tier covers it — the skill uses the same instrument and its engraved scales. The static model stays as a fallback.

Not tested in game: block registration, per-telescope saved aim, Altazimuth Mount skill checks, observations and failure messages remain with the gameplay implementation and the separate astronomy-sky dependency. No gameplay Java is changed here.
