# Local TC4R dependency

TC4R is a user-supplied build and is not redistributed. The dependency manifests
are tracked; JARs and POMs are ignored. Supply the artifacts listed in
`DEPENDENCIES.json` (Forge) and `DEPENDENCIES.1.21.1-neoforge.json` (NeoForge),
or point Gradle at their Maven root with `-Ptc4Repository=<maven root>`.

Each manifest records the source revision, Maven coordinate, in-game runtime
version and SHA-256 of the runtime JAR, public API JAR, sources JAR and POM.
Keep those files and the matching node's `gradle.properties` in sync.
`verifyLocalDependencies` validates all recorded hashes; `verifyPublicApiIntegrations`
compiles the migrated integration family with the public API JAR as its core input.

The Maven version differs from the in-game mod version. The node's
`thaumcraft_runtime_version` is the minimum required runtime version in loader metadata.

CI receives both Maven trees as one `.tar.gz` whose root holds this directory's
contents, served through the `TC4R_ARCHIVE_URL` repository variable and
`TC4R_ARCHIVE_TOKEN` secret; see `.github/actions/forge-setup`.

For TC4R 20777, use the runtime, API and sources JARs from the 1.20.1-forge-20777-dev.zip / 1.21.1-neoforge-20777-dev.zip kits plus their release zips, renamed to the paths in the manifests.
Use the following POM with a final newline, replacing `LOADER` with `forge`
or `neoforge`.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.tc4port</groupId>
  <artifactId>thaumcraft-LOADER</artifactId>
  <version>0.1.0-20777</version>
</project>
```
