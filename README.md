# Mcraft Voice Chat

A public, reusable Paper plugin by [Central Macatuba](https://github.com/centralmacatuba) that implements the `minecraft-web-client:voice-chat` plugin-message protocol and connects Minecraft players to LiveKit.

[Leia em português brasileiro](README.pt.md).

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
- Force-muted participants receive tokens without the LiveKit publish grant when the backend honors the contract.
- Server-side mute/unmute requests through a separate authenticated moderation endpoint.
- Persisted mute UUIDs, restored after reconnects and server restarts.
- Same-world proximity calculation and changed-payload suppression.
- `/mcraftvoice status`, `mute <player>`, and `unmute <player>`.
- CI build/test workflow, artifact upload, Dependabot, and tag-based GitHub releases.

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

See [Backend contract (English)](docs/backend-contract.md) and [Contrato do backend (Português)](docs/backend-contract.pt-BR.md).

The token endpoint must issue an HS256 LiveKit JWT with the exact player UUID in `sub`, the requested room in `video.room`, `roomJoin=true`, `canSubscribe=true`, and a short expiry. It must set `canPublish=false` for a force-muted player.

The moderation endpoint must persist mute state and enforce it against connected LiveKit participants. A 2xx response means the backend has accepted and durably stored the policy. A client-side `forceMuted` hint is not server-side enforcement.

The plugin validates JWT claims but does not cryptographically verify the signature. Signing and signature trust remain with the backend and LiveKit.

## Configuration

Set `MCRAFT_VOICE_BACKEND_TOKEN` in the Paper server process environment. Do not place the secret in `config.yml` or source control.

```yaml
voice-chat:
  enabled: true
  token-provider:
    endpoint: "https://backend.example/voice/token"
    bearer-token-env: "MCRAFT_VOICE_BACKEND_TOKEN"
    request-timeout-seconds: 10
    max-token-lifetime-seconds: 900
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

Permissions: `mcraftvoice.use` (default everyone) and `mcraftvoice.moderate` (default operators).

## Requirements and build

- Paper API 1.21.11; Java 21; Gradle 9.9.2 or compatible Gradle 9.x.
- A compatible Minecraft Web Client build.
- A trusted backend implementing the documented token and moderation contracts.
- LiveKit Cloud or a reachable self-hosted LiveKit deployment.

```bash
gradle --no-daemon clean test build
```

The plugin JAR is generated in `build/libs/`. CI uploads it as a workflow artifact; pushing a `v*` tag builds and publishes a GitHub Release. Velocity support and multi-server coordination are outside the initial scope.

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), and [SECURITY.md](SECURITY.md). Never publish LiveKit secrets, backend credentials, JWTs, player data, or production logs.

## License

MIT. See [LICENSE](LICENSE).
