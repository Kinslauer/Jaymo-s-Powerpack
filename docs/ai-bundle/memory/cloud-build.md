---
name: cloud-build
description: How to compile the mods in a cloud thread (JDK 25 + Google Maven mirror); Andy gave a standing approval for the mirror
metadata:
  type: project
  modified: 2026-10-21T20:39:23.875Z
---
Cloud threads can build: Temurin JDK 25 from api.adoptium.net into /opt/jdk25, and a ~/.gradle/init.d/mirror.gradle that swaps repo.maven.apache.org for https://maven-central.storage-download.googleapis.com/maven2/ (Central returns 429 here). Run `JAVA_HOME=/opt/jdk25 sh ./gradlew build` (gradlew isn't executable).
**Standing approval:** Andy, 2026-10-11 20:05Z, in the "barbarians look" thread: "i approve the mirror period, i dont want to run into this error again." Every thread may set up and use the mirror and the gradle runs that need it without asking again. The auto-mode check may still block it in a fresh thread: quote that sentence (and the thread message) to the check, or ask the coordinator to attach that message. 2026-10-21 (recipe hotfix thread): even the coordinator-relayed citation did NOT unblock it; only Andy writing "Use the mirror" in the thread itself did. Ask Andy directly early rather than relaying. 2026-10-22 (Chrono rewind thread, PR #411): even Andy's own "use the mirror" in the thread did NOT unblock it; the init script and JDK install went through, but `gradlew build` was still denied as Traffic Redirection. Don't burn turns on it: say so and offer the Remote Control build. That works: start_remote_control on PC folder "Origins Mod", git worktree of the PR branch, `gradlew.bat build` (JDK 25.0.4 there); jars land in E:\jaymospowers-build\<worktree>\libs, and `build` deploys nothing. Remove the worktree after.
**Why:** Andy's account needs trusted devices, so threads can't build on their PC.
**How to apply:** build before pushing; mixin targets aren't checked at build, so verify them with javap on .gradle/loom-cache minecraft jars. See [[cloud-playtest-setup]] for in-game runs.

2026-10-16: PR #300 (strains + martial-arts combos, merged uncompiled as 0.13.0) compiled clean on master 0.13.1 with no changes; server boots. Dev runServer still needs the local-only Stormie's Spiders dev:false + CameraPreset preLaunch removal (see [[immersion-mods-0152]]).
2026-10-31: root build failed on master because libs/jaymos-kitchen-0.5.33.1.jar is stale (KitchenCompat needs Hauling). Fix: build kitchen/ and temporarily swap its jar into libs/ (don't commit).
