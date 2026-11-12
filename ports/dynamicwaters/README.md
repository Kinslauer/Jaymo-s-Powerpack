# Dynamic Waters

A mod for NeoForge and Fabric on Minecraft 1.21.1 and Forge on 1.20.1 that reworks water in world generation: mountain
rivers routed over the real terrain, lowland rivers with deltas, mountain lakes, riverbanks and river currents.

## Building

One source tree builds every version with [Stonecutter](https://stonecutter.kikugie.dev). Gradle needs Java 25.

```
./gradlew :1.21.1-neoforge:build :1.21.1-fabric:build :1.20.1-forge:build -x javadoc -x javadocJar
```

Each jar is written to `versions/<version>/build/libs`. Replace `build` with `runClient` to start the game.

## AI assistance

Starting with version 13.0, AI has become a more significant part of my development workflow. I use Claude Opus 5.5 by Anthropic to assist with programming, problem-solving, code improvements, and other parts of development.

In my opinion, AI has become incredibly powerful. It can work at a speed that would be difficult for several programmers to match, sometimes solving in minutes what could otherwise take hours or even days. I believe this is a major shift in software development, and I don't see a reason to ignore these tools.

That said, I still write a lot of code myself. Not everything needs AI assistance, and many changes, smaller features, and quick fixes are still implemented manually. I decide what needs to be done, review the code, test changes in-game, find problems, and determine what needs further improvement. AI helps me work faster, but I remain responsible for the implementation and the direction of the project.

I also occasionally use AI as a translator and to improve my English. I originally write in my native language, Polish, and use translation assistance to make sure that descriptions, documentation, announcements, and other project-related text are clear and grammatically correct. English isn't my first language, and I want the information I publish to be as accurate and understandable as possible.

Ultimately, AI is a tool that helps me overcome limitations, work more efficiently, and focus on developing Dynamic Waters. The ideas, decisions, direction, and responsibility for the project remain mine.

I understand that the use of AI is still a controversial topic in parts of the modding community, and I'm sorry if this disappoints anyone. I respect people's opinions, but this is simply how I choose to approach development.

## Licence

Dynamic Waters is licensed under the [Mozilla Public License 2.0](LICENSE).

Some files are adapted from [TerraFirmaCraft](https://github.com/TerraFirmaCraft/TerraFirmaCraft) (EUPL v1.2) and
from [Dynamic Waters Async Rivers](https://github.com/erutobusiness/dynamicwaters-asyncrivers) (MIT). They keep
those licences. The list is in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
