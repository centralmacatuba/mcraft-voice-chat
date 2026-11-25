# Mcraft Voice Chat

A public Paper plugin by [Central Macatuba](https://github.com/centralmacatuba) that implements the `minecraft-web-client:voice-chat` plugin-message protocol and integrates Minecraft players with a LiveKit room.

[Leia em português brasileiro](README.pt.md).

The plugin is designed to be reusable by independent Minecraft servers. Server-specific infrastructure, credentials, room names, and moderation policies belong in each server's configuration, not in this repository.

> **Project status:** Planning and initial implementation. The protocol framing has been checked against the current `minecraft-web-client` client source. End-to-end compatibility with a live Minecraft Web Client and a LiveKit deployment is not yet verified.

## Goals

- Implement the existing `minecraft-web-client:voice-chat` channel without requiring a modified client.
- Target Paper first; keep the core logic isolated so a future Velocity integration remains possible.
- Keep voice media out of Minecraft plugin messages. WebRTC audio travels directly between clients and LiveKit.
- Coordinate voice sessions, proximity subscriptions, and moderation from the Minecraft server.
- Support LiveKit Cloud and self-hosted LiveKit through a configurable token backend.
- Keep all credentials and server-specific endpoints out of source control.
- Provide tests, reproducible builds, security checks, and clear operator documentation.

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

The Paper plugin:

- announces voice support to compatible clients;
- receives client `enable` and `disable` actions;
- requests an individual, short-lived LiveKit token from the configured backend;
- sends the LiveKit connection configuration to the client;
- calculates the server-authoritative set of nearby, voice-enabled players;
- sends proximity and moderation state to clients;
- clears player state on disconnect and handles asynchronous token failures.

The client remains responsible for microphone capture, LiveKit WebRTC connectivity, audio subscriptions, 3D positioning, and spatial attenuation.

## Client protocol

Channel: `minecraft-web-client:voice-chat`

The current client registers a packet structure containing two Minecraft protocol strings:

1. `action`: `pstring` with an `i16` length prefix;
2. `json`: `pstring` with an `i16` length prefix.

The JSON string contains an object payload. This is **not** a raw JSON plugin message and must not be encoded with Java's `DataOutputStream.writeUTF` modified-UTF format.

### Client → server

| Action | Payload | Meaning |
| --- | --- | --- |
| `enable` | `{}` | Player opts into voice chat. |
| `disable` | `{}` | Player leaves voice chat. |

### Server → client

| Action | Payload | Meaning |
| --- | --- | --- |
| `available` | `{}` | This server supports voice chat. |
| `config` | `VoiceConfig` | LiveKit connection details for this player. |
| `nearby` | `NearbyPayload` | Players the client should currently be able to hear, plus forced-mute identities. |

Expected `VoiceConfig` fields (matching the client interface):

```json
{
  "url": "wss://your-livekit-host",
  "room": "minecraft-survival",
  "token": "<short-lived-participant-token>",
  "identity": "<minecraft-player-uuid>",
  "hearingRange": 32,
  "moderated": true
}
```

Expected `NearbyPayload` shape:

```json
{
  "players": [
    {
      "identity": "<minecraft-player-uuid>",
      "username": "Player"
    }
  ],
  "forceMuted": []
}
```

The protocol is based on the client implementation, not an independently invented message format. Re-check the upstream client source when updating compatibility.

## Token backend contract

The plugin must never expose a LiveKit API secret to a browser or include it in this repository. It calls a trusted HTTPS backend using a server-side bearer credential.

The initial HTTP provider contract is:

- Request: `POST <token-provider.endpoint>`
- Header: `Authorization: Bearer <server-side-secret>`
- JSON request body:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "username": "Player",
  "serverId": "mcraft",
  "room": "minecraft-survival"
}
```

- JSON response:

```json
{
  "url": "wss://your-livekit-host",
  "room": "minecraft-survival",
  "token": "<short-lived-participant-token>"
}
```

The backend must issue a token whose participant identity (`sub`) is the supplied Minecraft UUID, restrict the token to the requested room, apply appropriate publish/subscribe grants, and use a short expiration. The backend must authenticate the server credential and validate the request; it must not blindly trust arbitrary player identities from public callers.

## Configuration

The implementation will expose server-specific settings through `config.yml`. Secrets should be injected through environment variables or another server-side secret store, never committed to Git.

See the generated configuration and installation documentation as implementation progresses.

## Compatibility and requirements

- Platform: Paper (initial target).
- Minecraft API baseline: 1.21.11.
- Java: 21.
- Client: a Minecraft Web Client build containing the `minecraft-web-client:voice-chat` implementation.
- Voice transport: LiveKit over WebRTC; the Minecraft proxy is not an audio relay.

Velocity and multi-backend network coordination are explicitly outside the initial implementation scope.

## Development roadmap

### Phase 1 — Protocol and Paper foundation
- [ ] Create the Gradle project and plugin entry point.
- [ ] Implement exact `pstring/i16` packet framing and JSON payload validation.
- [ ] Register incoming/outgoing plugin messaging.
- [ ] Announce `available` to compatible clients.
- [ ] Handle `enable` and `disable` safely.
- [ ] Add unit tests for packet encoding/decoding.

### Phase 2 — LiveKit session provisioning
- [ ] Implement the authenticated token-backend HTTP client.
- [ ] Validate backend responses and bind token identity to the Minecraft UUID.
- [ ] Send the exact `config` payload expected by the client.
- [ ] Handle timeouts, errors, duplicate requests, and disconnect races.

### Phase 3 — Proximity and moderation
- [ ] Calculate nearby voice-enabled players in the same world.
- [ ] Send `nearby` only when the payload changes.
- [ ] Support configurable hearing range and update interval.
- [ ] Add server-enforced mute state and permission integration.
- [ ] Clean up all state on world changes, quit, and plugin shutdown.

### Phase 4 — Verification and release
- [ ] Test against the unmodified Minecraft Web Client.
- [ ] Verify two-player and multi-player audio through LiveKit.
- [ ] Test reconnects, token expiry, mute, world changes, and proxy forwarding.
- [ ] Complete operator documentation and release automation.
- [ ] Review dependency security and build reproducibility.

## Development

The project uses Gradle and the Java toolchain configured by the build. Run:

```bash
gradle test
gradle build
```

The resulting plugin JAR is placed in `build/libs/`.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
