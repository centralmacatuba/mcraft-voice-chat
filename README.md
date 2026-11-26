# Mcraft Voice Chat

A public, reusable Paper plugin by [Central Macatuba](https://github.com/centralmacatuba) that implements the `minecraft-web-client:voice-chat` plugin-message protocol and connects Minecraft players to LiveKit.

[Leia em português brasileiro](README.pt.md).

This is an independent project and is not affiliated with or endorsed by the Prefeitura Municipal de Macatuba.

The plugin is intended for independent Minecraft servers. Server-specific infrastructure, credentials, room names, and moderation policies belong in each server installation, not in this repository.

> **Project status:** Initial Paper implementation with token provisioning, proximity updates, and an HTTP moderation integration contract. Live compatibility with the upstream client and a real LiveKit deployment still requires end-to-end testing.

## Architecture

```text
Minecraft Web Client
  | minecraft-web-client:voice-chat (control/configuration only)
  v
Paper server + Mcraft Voice Chat
  | HTTPS token and moderation requests
  v
Trusted voice backend
  | LiveKit server SDK / administrative API
  v
LiveKit room

Minecraft Web Client <====== WebRTC audio ======> LiveKit
```

Audio travels directly over WebRTC between the client and LiveKit. Minecraft plugin messages carry control/configuration data only.

## Implemented features

- Paper plugin for the existing `minecraft-web-client:voice-chat` channel.
- Protocol framing with two Minecraft `pstring` values: action and JSON payload.
- Per-player short-lived LiveKit token provisioning through an authenticated HTTPS backend.
- JWT claim checks for algorithm, participant UUID, room, grants, and expiry.
- The backend's persisted mute policy is authoritative; the local force-muted flag is deny-only, and the backend may restrict publishing even when the local mirror is stale.
- Server-side mute/unmute requests through a separate authenticated moderation endpoint.
- Persisted mute UUIDs, restored after reconnects and server restarts.
- Offline moderation for player identities already present in the server's offline-player cache.
- Per-player ordered moderation requests to prevent stale mute/unmute completions from overwriting newer state.
- Same-world proximity calculation and changed-payload suppression.
- Rate-limited malformed-packet warnings and bounded token-provider response parsing.
- `/mcraftvoice status`, `mute <player>`, and `unmute <player>`.

## Client protocol

Channel: `minecraft-web-client:voice-chat`.

Each message contains two protocol strings:
1. `action`: UTF-8 `pstring` with a signed `i16` length prefix;
2. `json`: UTF-8 `pstring` with a signed `i16` length prefix.

The JSON value is an object. It is not raw JSON and must not use Java's modified-UTF `DataOutputStream.writeUTF` encoding.

Client → server: `enable`, `disable`.
Server → client: `available`, `config`, `nearby`.

## Backend integration

The backend is a separate service and is not included in this repository. The plugin never receives the LiveKit API secret.

See [Backend contract (English)](docs/backend-contract.md), [Manual integration test plan](docs/testing.md), and [Contrato do backend (Português)](docs/backend-contract.pt-BR.md).

The token endpoint must issue an HS256 LiveKit JWT with the exact player UUID in `sub`, the requested room in `video.room`, `roomJoin=true`, `canSubscribe=true`, and a short expiry. It must set `canPublish=false` whenever the persisted backend policy OR the local deny-only hint requires a mute. The plugin rejects a token that grants publishing when its local hint is muted, while accepting stricter backend restrictions.

The moderation endpoint must persist mute state and enforce it against connected LiveKit participants. The backend's stored policy is authoritative: a false `forceMuted` request hint must never clear a stored mute. A 2xx response means the backend has accepted and durably stored the policy. A client-side `forceMuted` hint is not server-side enforcement.

The plugin validates JWT claims, including integer `exp` and optional `nbf` NumericDate values, but does not cryptographically verify the signature. Signing and signature trust remain with the backend and LiveKit. Keep token lifetime at 600 seconds or less. LiveKit refreshes access tokens for connected clients, so a client may hold a server-refreshed token after its original join token expires. LiveKit Cloud revokes tokens after permission changes/removal using `nbf`, with a default one-minute clock-skew buffer and a 10-minute revocation window. Self-hosted LiveKit updates permissions for connected participants (including unpublishing tracks when publishing is revoked) but does not invalidate already-issued or refreshed tokens for reconnects. A short initial TTL alone is not immediate revocation; enforce admission control in the backend.

## Configuration

Set `MCRAFT_VOICE_BACKEND_TOKEN` in the Paper server process environment. Do not place the secret in `config.yml` or source control.

```yaml
voice-chat:
  enabled: true
  token-provider:
    endpoint: "https://backend.example/voice/token"
    bearer-token-env: "MCRAFT_VOICE_BACKEND_TOKEN"
    request-timeout-seconds: 10
    max-token-lifetime-seconds: 600
  session:
    server-id: "my-server"
    room: "minecraft-survival"
    hearing-range: 32.0
    moderated: true
  moderation:
    endpoint: "https://backend.example/voice/moderation"
    muted-players: []
  proximity:
    update-interval-ticks: 10
```

Both backend endpoints must use HTTPS. The LiveKit URL returned to clients must use `wss://`; `ws://` is accepted only for loopback development.

If the moderation endpoint is not configured, moderation commands remain unavailable. This is intentional: a client-side `forceMuted` hint is not an enforcement boundary.

## Commands and permissions

- `/mcraftvoice status` — active voice sessions and moderation backend status.
- `/mcraftvoice mute <player>` — persist and enforce a participant mute through the backend.
- `/mcraftvoice unmute <player>` — remove the backend mute policy.
- Alias: `/mcvoice`.

Offline moderation resolves players from Paper's cached offline-player identities. A player must have joined the server at least once; arbitrary unknown names are not resolved through a blocking network lookup.

Permissions: `mcraftvoice.use` (default everyone) and `mcraftvoice.moderate` (default operators).

Revoking `mcraftvoice.use` causes the plugin to invalidate its local active/pending session state on the next proximity update. The current upstream plugin-message protocol has no server-to-client disable action, so this local cleanup does **not** forcibly disconnect an already connected LiveKit participant. Immediate remote revocation requires a backend participant-disconnect/admission-control operation and is not yet implemented by this plugin.

## Build and release

Requirements: Paper API 1.21.11, Java 21, and the checked-in Gradle Wrapper 9.9.2.

```bash
./gradlew --no-daemon clean test build
```

The plugin JAR is generated in `build/libs/`. CI validates YAML/JSON metadata, lints GitHub Actions workflows with actionlint, runs unit tests and MockBukkit server-lifecycle integration tests, builds the JAR, and uploads it as a workflow artifact. MockBukkit verifies the plugin lifecycle and command registration against a simulated Paper server; it does not replace real Minecraft Web Client, backend, or LiveKit end-to-end tests.

Releases use [Conventional Commits](https://www.conventionalcommits.org/) and [Release Please](https://github.com/googleapis/release-please). On pushes to `principal`, Release Please opens or updates a release pull request with the version and changelog. Merging that PR creates the `vX.Y.Z` tag and GitHub Release; the tag-triggered workflow then tests/builds the plugin and attaches the JAR to that existing release. Configure the repository secret `RELEASE_PLEASE_TOKEN` as described in [CONTRIBUTING.md](CONTRIBUTING.md). The manifest currently tracks `0.1.0` as the unreleased baseline.

Velocity support and multi-server coordination are outside the initial scope.

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), and [SECURITY.md](SECURITY.md). Never publish LiveKit secrets, backend credentials, JWTs, player data, or production logs.

## License

MIT. See [LICENSE](LICENSE).
