# repo cleanup: stray javac argument file removed
- Removes `kingdoms/src/javac.20261104_084443.args`, an argument file a crashed javac run left on 4 Nov. Nothing in the
  build reads it; no game change.
- Same PR (repo only, no mod change): root `genSources.log` and javac args, 12 committed `__pycache__` files, two old
  Sora's dagger drafts in tools/blockbench, and the finished docs AGENT_BRIEF_0.2, Kingdoms-Plan-0.3 and Plan-0.7.
