# Contributing

Thanks for helping improve Mcraft Voice Chat!

## Before opening a change

- Check existing issues and discussions to avoid duplicate work.
- Keep the plugin reusable for independent Paper servers. Do not add production credentials, endpoints, room identifiers, or deployment assumptions.
- Never commit API keys, LiveKit secrets, bearer tokens, player data, or production logs.
- Keep protocol changes compatible with the upstream `minecraft-web-client:voice-chat` implementation and document the upstream revision used for verification.
- Prefer small, focused changes with tests.

## Development requirements

- Java 21.
- Java 21; the checked-in Gradle Wrapper downloads and verifies Gradle 9.9.2.
- Paper API is resolved from the Paper Maven repository.

## Verify locally

```bash
./gradlew --no-daemon clean test build
```

The plugin JAR is written to `build/libs/`.

## Pull requests

Include the problem statement, implementation summary, tests added or updated, compatibility notes, and configuration/migration notes. Explicitly disclose when an integration test could not be run.

Never claim live LiveKit or client compatibility based only on unit tests.
