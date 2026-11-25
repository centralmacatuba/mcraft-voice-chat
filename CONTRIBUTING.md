# Contributing

Thanks for helping improve Mcraft Voice Chat!

## Before opening a change

- Check existing issues and discussions to avoid duplicate work.
- Keep the plugin reusable for independent Paper servers. Do not add production credentials, endpoints, room identifiers, or deployment assumptions.
- Never commit API keys, LiveKit secrets, bearer tokens, player data, or production logs.
- Keep protocol changes compatible with the upstream `minecraft-web-client:voice-chat` implementation and document the upstream revision used for verification.
- Prefer small, focused changes with tests.

## Development requirements

- Java 21; the checked-in Gradle Wrapper downloads and verifies Gradle 9.9.2.
- Paper API is resolved from the Paper Maven repository.

## Verify locally

```bash
./gradlew --no-daemon clean test build
```

The plugin JAR is written to `build/libs/`.

## Commit messages and releases

The project uses [Conventional Commits](https://www.conventionalcommits.org/) with Release Please to prepare version and changelog pull requests.

Use these prefixes:

- `feat:` for a user-facing feature (minor release).
- `fix:` for a bug fix (patch release).
- `perf:` for a performance improvement (patch release).
- `feat!:` or another type with `!` for a breaking change (major release).
- `docs:`, `test:`, `build:`, `ci:`, `refactor:`, and `chore:` for changes that normally do not trigger a release by themselves.

Examples:

```text
feat: add configurable voice distance
fix: handle expired LiveKit credentials
feat!: replace the backend token contract
docs: clarify Paper setup
```

Release Please runs on pushes to `principal`. It opens or updates a release pull request with the generated changelog and next version. Merge that release pull request to create the `vX.Y.Z` tag and GitHub release. The tag then triggers `.github/workflows/release.yml`, which runs the tests and builds the distributable plugin JAR.

### Required GitHub secret

Configure a repository secret named `RELEASE_PLEASE_TOKEN` containing a fine-grained personal access token scoped only to this repository, with:

- Contents: read and write.
- Issues: read and write.
- Pull requests: read and write.

This token is required because a tag or release created with the default `GITHUB_TOKEN` does not trigger another GitHub Actions workflow. Using this dedicated token allows the generated release tag to start the existing tag-based build and publishing workflow.

## Pull requests

Include the problem statement, implementation summary, tests added or updated, compatibility notes, and configuration/migration notes. Explicitly disclose when an integration test could not be run.

Never claim live LiveKit or client compatibility based only on unit tests.
