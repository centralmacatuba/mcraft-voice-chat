# Changelog

Notable changes to Mcraft Voice Chat are documented here.

The format is based on Keep a Changelog and the project follows Semantic Versioning.

## [Unreleased]

### Added
- Backend-enforced mute integration contract and HTTP moderation provider.
- Persistent local mute policy, restored across player reconnects and server restarts.
- CI build artifacts and tag-based GitHub releases.
- Contribution guidelines, code of conduct, and backend contract documentation.

### Changed
- Token requests include the force-muted state; muted participants must receive tokens without the LiveKit publish grant.
- JWT claim validation requires HS256 and checks publish permissions against moderation state.
- Plugin-message strings use strict UTF-8 handling.
- CI runs on feature branch pushes as well as the default branch.

### Security
- HTTP clients do not follow redirects, preventing bearer credentials from being forwarded to redirect targets.
