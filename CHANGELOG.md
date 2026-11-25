# Changelog

Notable changes to Mcraft Voice Chat are documented here.

The format is based on Keep a Changelog and the project follows Semantic Versioning.

## [Unreleased]

### Added
- Manual end-to-end integration test plans in English and Brazilian Portuguese.
- Gradle 9.9.2 Wrapper with pinned distribution checksum and CI verification.
- Backend-enforced mute integration contract and HTTP moderation provider.
- Persistent local mute policy, restored across player reconnects and server restarts.
- CI build artifacts and tag-based GitHub releases.
- Contribution guidelines, code of conduct, and backend contract documentation.

### Changed
- Default and maximum token lifetime reduced to 600 seconds.
- Gson is compile-only for the plugin artifact and explicitly available to tests.
- Token requests include the force-muted state; muted participants must receive tokens without the LiveKit publish grant.
- JWT claim validation requires HS256 and checks publish permissions against moderation state.
- Plugin-message strings use strict UTF-8 handling.
- CI runs on feature branch pushes as well as the default branch.

### Security
- JWTs are redacted from credential diagnostic output and malformed backend responses are not included in exception causes.
- HTTP clients do not follow redirects, preventing bearer credentials from being forwarded to redirect targets.
