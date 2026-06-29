# Development

Maintenance notes for working on this project itself. From time to time, it is worth to update the build tooling and dependencies.

## Update gradlew wrapper

```shell
./gradlew wrapper --gradle-version latest
```

## Check for dependency updates

All the gradle dependencies are managed by the [libs.versions.toml](gradle/libs.versions.toml) file in the `gradle` dir.

It is easy to check for the latest versions by running:

```shell
./gradlew dependencyUpdates --no-parallel
```

This reports the available updates; unstable candidates (`alpha`, `beta`, `rc`) are filtered out by the `dependencyUpdates` task configuration in [build.gradle.kts](build.gradle.kts). Apply the ones you want by editing [libs.versions.toml](gradle/libs.versions.toml) manually, or running:

```shell
./gradlew versionCatalogUpdate
```

Which will apply changes automatically to the [libs.versions.toml](gradle/libs.versions.toml).
