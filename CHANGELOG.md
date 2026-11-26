# Changelog

Notable changes to Mcraft Voice Chat are documented here.

The format is based on Keep a Changelog and the project follows Semantic Versioning.

## [Unreleased]

### Added
- Manual end-to-end integration test plans in English and Brazilian Portuguese.
- Gradle 9.9.2 Wrapper with pinned distribution checksum and CI verification.
- Backend-enforced mute integration contract and HTTP moderation provider.
- Revisioned, idempotent moderation mutations with durable local revision counters and backend compare-and-set requirements.
- Bounded token response subscriber with total-deadline cancellation for stalled HTTP bodies.
- Persistent local mute policy, restored across player reconnects and server restarts.
- CI build artifacts and tag-based GitHub releases.
- Contribution guidelines, code of conduct, and backend contract documentation.
- Runtime permission revalidation that invalidates local active and pending sessions when access is revoked.

### Changed
- Default and maximum token lifetime reduced to 600 seconds.
- Gson is compile-only for the plugin artifact and explicitly available to tests.
- Token requests include a deny-only force-muted hint; the backend's persisted policy may impose stricter restrictions.
- JWT claim validation requires HS256 and validates integer exp and required nbf NumericDate claims.
- Plugin-message strings use strict UTF-8 handling.
- Gradle dependency verification with SHA-256 checksums and actionlint validation for GitHub Actions workflows.
- Voice status distinguishes local configuration from backend connectivity, which is checked only when requests are made.
- CI runs on pushes to principal, pull requests targeting principal, and manual dispatch.

### Security
- JWTs are redacted from credential diagnostic output and malformed backend responses are not included in exception causes.
- HTTP clients do not follow redirects, preventing bearer credentials from being forwarded to redirect targets.
- Insecure LiveKit `ws://` URLs are rejected by default; loopback-only use requires an explicit local-testing opt-in.
- Active and pending sessions are invalidated when the player loses mcraftvoice.use.
