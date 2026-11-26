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

The request's `forceMuted` field is a deny-only hint from Paper. The backend must consult its own persisted moderation state and must never let `forceMuted=false` override a stored mute. Compute the effective mute as the persisted policy OR the request hint. The backend may therefore return `canPublish=false` even when the local hint is false. The plugin rejects a token that grants publishing when the local hint is true, but does not reject a stricter backend decision.

The JWT must use HS256; set `sub` to the exact Minecraft UUID and `video.room` to the requested room; grant `video.roomJoin=true` and `video.canSubscribe=true`; set `video.canPublish=true` only when the effective mute policy allows publishing; and expire within `max-token-lifetime-seconds` (keep this at 600 seconds or less). The `exp` claim must be an integer NumericDate. If `nbf` is present, it must be an integer NumericDate and must not be more than 60 seconds in the future to account for clock skew.

LiveKit refreshes access tokens for connected clients, so an active client may hold a server-refreshed token rather than the original join token. On LiveKit Cloud, permission updates and participant removal revoke tokens using the `nbf` timestamp; the default cutoff includes a one-minute clock-skew buffer, and revocation remains effective for 10 minutes. On self-hosted LiveKit, permission updates affect connected participants and revoke publishing (which unpublishes existing tracks), but do not invalidate already-issued or refreshed tokens for future reconnects. A short initial TTL alone is therefore not an immediate-revocation guarantee. Enforce admission policy in the backend and do not issue new tokens after access is revoked. See the [LiveKit token and grant documentation](https://docs.livekit.io/frontends/reference/tokens-grants/) and [participant management documentation](https://docs.livekit.io/intro/basics/rooms-participants-tracks/participants/).

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

The plugin currently invalidates its local active/pending session state when `mcraftvoice.use` is revoked, but the upstream protocol has no server-to-client disable action and this repository does not yet call a backend participant-removal/admission-control operation for that event. An already connected LiveKit participant may therefore remain connected until the client disconnects or its session ends. Do not treat local permission revalidation as immediate remote revocation.

## Operations

Use one backend credential per Paper server, keep LiveKit API credentials only in the backend secret store, restrict administrative capability to required rooms, and test token issuance, mute, unmute, reconnect, and restart against a non-production room before deployment.
