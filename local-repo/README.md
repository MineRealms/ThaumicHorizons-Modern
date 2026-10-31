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
