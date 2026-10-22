# Rosetta probes

Method bodies run inside the live dev server by the RosettaRemoteDebugBridge (see the memory note
`as-live-debug-bridge`). Run them in order against `./gradlew.bat runServer`:

```bash
T=$(cat run-server/RosettaRemoteDebugBridge/remote-token.txt)
python ../RainJava-work/RosettaRemoteDebugBridge/legacy/tools/rosetta_remote.py --port 48790 --token "$T" exec "$PWD/probes/server/01_environment.java"
```

The bridge jar must be the non-reobfuscated build (`gradlew jar -x reobfJar`): the reobfuscated artifact
carries a mangled `invokedynamic` name that crashes `AddPackFindersEvent` on 1.20.1. Its `assets`/`data`
folders must also not exist when it boots — it recreates them before the event fires.

| probe | checks |
|---|---|
| 01_environment | mods, registries, aspects, research, infusion data loaded |
| 02_grant_and_form_vat | research grant through the admin context; builds and forms a vat at a fixed spot (`run-server/rosetta_probes/vat_origin.txt`) |
| 03_curative_wired | jar → tube → tube → the vat's connector, a hurt pig inside; every block outside the shell |
| 04_curative_check | run ~30 s after 03: the pig is healed from the jar's victus |
| 05_vat_state | snapshot of the vat |
| 06_creature_infusion | pedestals + pig + essentia → awakened blood infusion applied, pedestals consumed |
| 07_real_click_path | the whole player gesture path through `BlockState#use`: in through the glass, out through the water, creature in through a soul jar, ONE wand tap on the matrix, effigy in and back out |

Notes learned the hard way:
- A warded jar only connects through its top face, and a tube serves but never pushes, so the consumer has to
  pull; force-load the chunk or nothing ticks.
- Never drive a block through a vat block: replacing one is a break, and breaking any vat block takes the
  whole vat apart. The break is re-entrant inside `LevelChunk.setBlockState`, and when the replacement has a
  block entity of its own the old vat entity is retagged onto it and survives — stale, and a crash in every
  `isMaster()` until the guard. Place tubes and jars outside the 3×3 footprint.
