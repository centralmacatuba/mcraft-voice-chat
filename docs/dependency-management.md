# Dependency management

## Paper API 1.21.11

This project targets Minecraft/Paper 1.21.11 and Java 21. Paper's documented Maven coordinate for 1.21.11 and older is `1.21.11-R0.1-SNAPSHOT`. Paper's project setup documentation explicitly states that, before the 26.1 version line, there is no supported way to select a specific Paper API build. The newer `{VERSION}.build.{BUILD}-stable` coordinates apply to 26.1 and later and cannot be adopted here without changing the supported Minecraft line and Java toolchain.

For that reason, do not replace the 1.21.11 dependency with a guessed release coordinate or a timestamped filename. Keep the official Paper coordinate and use Gradle dependency verification as a fail-closed integrity check:

- `gradle/verification-metadata.xml` verifies the resolved Paper API artifacts and metadata using SHA-256.
- If Paper republishes the SNAPSHOT metadata or artifact, a clean build must fail verification rather than silently accepting new bytes.
- Before intentionally updating the Paper API snapshot, resolve it from a clean Gradle cache, inspect the dependency changes, regenerate SHA-256 metadata, and commit the reviewed changes together.
- Never use `--write-verification-metadata` as an automatic CI repair or disable verification to bypass a mismatch.
- Release builds must run from the exact source commit and pass the same verification.

Revisit this decision only when the project intentionally raises its supported server version to Paper 26.1 or later and adopts the corresponding Java toolchain. At that point, pin an immutable `{VERSION}.build.{BUILD}-stable` coordinate instead of using a dynamic version.

## Transitive dependencies

Paper API and MockBukkit bring transitive dependencies into the compile/test graph. Keep the dependency verification file complete and review OSV findings against the resolved dependency graph and the final plugin JAR. A compile-only dependency is not bundled into the plugin JAR, but that fact does not excuse ignoring a vulnerable library used by test/runtime tooling.
