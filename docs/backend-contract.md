# Voice backend contract

This document defines the server-to-server contract consumed by the Paper plugin. The backend is a separate service and is not implemented or hosted by this repository.

## Security requirements

- Expose both endpoints over HTTPS.
- Authenticate the Paper server with the configured Bearer token. Use a distinct, high-entropy credential per server installation.
- Never return the LiveKit API secret.
- Do not trust public clients to choose arbitrary participant identities, rooms, or server IDs.
- Apply rate limits and audit administrative moderation changes.
- Never log Authorization headers, JWTs, or full response bodies.
- Do not redirect requests to another host.

## Token endpoint

**Request:** `POST <token-provider.endpoint>`

Headers: `Authorization: Bearer <server credential>`, `Content-Type: application/json`, `Accept: application/json`.

Body:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "username": "Player",
  "serverId": "mcraft",
  "room": "minecraft-survival",
  "forceMuted": false
}
```

Successful response:

```json
{
  "url": "wss://livekit.example.net",
  "room": "minecraft-survival",
  "token": "<short-lived LiveKit participant JWT>"
}
```

The request's `forceMuted` field is a deny-only hint from Paper. The backend must consult its own persisted moderation state and must never let `forceMuted=false` override a stored mute. Compute the effective mute as the persisted policy OR the request hint.

The JWT must use HS256; set `sub` to the exact Minecraft UUID and `video.room` to the requested room; grant `video.roomJoin=true` and `video.canSubscribe=true`; set `video.canPublish=true` only when `forceMuted=false`; and expire within `max-token-lifetime-seconds` (keep this at 600 seconds or less).\n\nLiveKit Cloud revokes existing participant tokens when permissions change, with a 10-minute revocation window. On self-hosted LiveKit, updating permissions affects connected participants and revokes their publishing ability, but does not invalidate already-issued tokens for future reconnects. A stale token may remain usable until it expires. Keep TTL short and use additional admission control if immediate reconnect revocation is a requirement. See the [LiveKit token and grant documentation](https://docs.livekit.io/frontends/reference/tokens-grants/) and [participant management documentation](https://docs.livekit.io/intro/basics/rooms-participants-tracks/participants/).

The plugin decodes and validates claims but does not cryptographically verify the JWT signature. Signing and signature trust remain the responsibility of the trusted backend and LiveKit.

## Moderation endpoint

**Request:** `POST <voice-chat.moderation.endpoint>`

Use the same authentication and content headers as the token endpoint.

Body:

```json
{
  "playerUuid": "00000000-0000-0000-0000-000000000000",
  "serverId": "mcraft",
  "room": "minecraft-survival",
  "muted": true
}
```

Return a 2xx status only after the mute policy is durably stored and the LiveKit operation is accepted. Return non-2xx if it cannot be applied; the plugin then leaves its local mute state unchanged.

The backend must:
1. validate that the authenticated server may manage the supplied server ID and room;
2. persist the policy by server, room, and participant UUID;
3. update connected participants so `canPublish=false` when muted and restore the intended permission when unmuted;
4. apply persisted policy to future tokens, including after reconnects;
5. make repeated requests idempotent.

A client-side `forceMuted` hint is not enforcement. The LiveKit participant permission is the enforcement boundary. If the participant is offline, persist the policy and ensure the next token has publishing disabled.

## Operations

Use one backend credential per Paper server, keep LiveKit API credentials only in the backend secret store, restrict administrative capability to required rooms, and test token issuance, mute, unmute, reconnect, and restart against a non-production room before deployment.
