# Mcraft Voice Chat

A public Paper plugin by [Central Macatuba](https://github.com/centralmacatuba) that implements the `minecraft-web-client:voice-chat` plugin-message protocol and integrates Minecraft players with a LiveKit room.

[Leia em português brasileiro](README.pt.md).

The plugin is designed to be reusable by independent Minecraft servers. Server-specific infrastructure, credentials, room names, and moderation policies belong in each server's configuration, not in this repository.

> **Project status:** Initial Paper implementation. Protocol framing and message handling are implemented; token claims are checked and basic moderation commands are available. End-to-end compatibility with a live Minecraft Web Client and LiveKit deployment still requires integration testing.

## Features

- Implements `minecraft-web-client:voice-chat` without modifying the client.
- Paper-first architecture with a clean separation between protocol, token provider, player state, and proximity calculation.
- LiveKit audio travels directly over WebRTC; Minecraft plugin messages carry control/configuration data only.
- Per-player short-lived token provisioning through an authenticated HTTPS backend.
- JWT claim checks for participant UUID, room, room-join grant, and expiration.
- Server-calculated proximity list, configurable hearing range, and changed-payload suppression.
- `/mcraftvoice status`, `mute <player>`, and `unmute <player>` commands.
- Secrets are read from an environment variable and are not stored in the repository.

## Architecture

```text
Minecraft Web Client
  |  minecraft-web-client:voice-chat (control/configuration only)
  v
Paper server + Mcraft Voice Chat
  |  authenticated HTTPS request for a short-lived participant token
  v
Voice token backend
  |
  +---- LiveKit credentials / participant grants

Minecraft Web Client <====== WebRTC audio ======> LiveKit
```

The client handles microphone capture, LiveKit WebRTC connectivity, subscriptions, 3D positioning, and spatial attenuation. The plugin handles protocol messages, token provisioning, server-side player state, proximity lists, and moderation state.

## Client protocol

Channel: `minecraft-web-client:voice-chat`

Each plugin message contains two Minecraft protocol strings:

1. `action`: `pstring` with a signed `i16` length prefix;
2. `json`: `pstring` with a signed `i16` length prefix.

The second string contains a JSON object. It is not raw JSON and must not use Java's modified-UTF `DataOutputStream.writeUTF` encoding.

### Client → server

| Action | Payload | Meaning |
| --- | --- | --- |
| `enable` | `{}` | Player opts into voice chat. |
| `disable` | `{}` | Player leaves voice chat. |

### Server → client

| Action | Payload | Meaning |
| --- | --- | --- |
| `available` | `{}` | Server supports voice chat. |
| `config` | `VoiceConfig` | LiveKit connection details for this player. |
| `nearby` | `NearbyPayload` | Nearby participants and forced-muted identities. |

## Token backend

The plugin never receives or exposes the LiveKit API secret. It calls a trusted HTTPS endpoint using a server-side Bearer credential.

The backend must authenticate the server, validate the requested player and room, and mint a short-lived LiveKit JWT with the player's UUID as `sub`, the requested room as `video.room`, and a room-join grant.

The plugin inspects the JWT claims to reject mismatched identities, rooms, missing room-join grants, expired tokens, and tokens whose expiry exceeds the configured maximum. This is claim validation, not cryptographic signature verification; signature validation remains the responsibility of LiveKit and the trusted token backend.

## Configuration

The main configuration is `plugins/McraftVoiceChat/config.yml`:

```yaml
voice-chat:
  enabled: true
  token-provider:
    endpoint: "https://your-backend.example/voice/token"
    bearer-token-env: "MCRAFT_VOICE_BACKEND_TOKEN"
    request-timeout-seconds: 10
    max-token-lifetime-seconds: 900
  session:
    server-id: "mcraft"
    room: "minecraft-survival"
    hearing-range: 32.0
    moderated: true
  proximity:
    update-interval-ticks: 10
```

Set `MCRAFT_VOICE_BACKEND_TOKEN` in the server process environment. Do not put the secret in `config.yml` or source control.

The LiveKit URL must use `wss://`. Plain `ws://` is accepted only for loopback development endpoints.

## Commands and permissions

- `/mcraftvoice status` — shows the number of players with an active voice session.
- `/mcraftvoice mute <player>` — adds the player identity to the forced-mute list sent to nearby clients.
- `/mcraftvoice unmute <player>` — removes the player from that list.
- Alias: `/mcvoice`.

Permissions:

- `mcraftvoice.use` — join voice chat (default: everyone).
- `mcraftvoice.moderate` — run moderation commands (default: operators).

**Moderation limitation:** `mute` controls playback of that participant on compatible clients through the protocol's `forceMuted` field. It does not revoke the participant's LiveKit microphone-publishing grant. Enforcing publish mute at the media server requires an additional LiveKit administrative integration in the trusted backend.

## Requirements

- Paper API 1.21.11.
- Java 21.
- A Minecraft Web Client build containing `minecraft-web-client:voice-chat`.
- A token backend that implements the documented contract.
- LiveKit Cloud or a reachable self-hosted LiveKit deployment.

Velocity support and multi-backend network coordination are outside the initial scope.

## Build

```bash
gradle test
gradle build
```

The plugin JAR is generated in `build/libs/`.

## License

MIT. See [LICENSE](LICENSE).
