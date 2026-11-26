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
- Require LiveKit URLs to use WSS. Insecure WS is disabled by default and may only be opted into for loopback local testing with `voice-chat.token-provider.allow-insecure-loopback: true`.

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

The JWT must use HS256; set `sub` to the exact Minecraft UUID and `video.room` to the requested room; grant `video.roomJoin=true` and `video.canSubscribe=true`; set `video.canPublish=true` only when the effective mute policy allows publishing; and expire within `max-token-lifetime-seconds` (keep this at 600 seconds or less). The `exp` claim must be an integer NumericDate. `nbf` is required for LiveKit token-revocation compatibility. It must be an integer NumericDate and must not be more than 60 seconds in the future to account for clock skew. Require `exp > nbf`, and ensure `exp - nbf` does not exceed `max-token-lifetime-seconds`; checking only the remaining lifetime from the current clock is insufficient.

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
  "muted": true,
  "revision": 42,
  "operationId": "11111111-1111-4111-8111-111111111111"
}
```

The plugin also sends `Idempotency-Key: <operationId>`. The revision is allocated monotonically and persisted locally before dispatch. A skipped revision is harmless; a revision must never be reused for a different operation.

Return a 2xx status only after the mute policy is durably stored and the LiveKit operation is accepted. Return `409 Conflict` for a stale revision (or an operation-ID/revision reuse with a different payload); other non-2xx responses indicate that the mutation was not confirmed. The plugin leaves its local mute mirror unchanged on every non-2xx response.

The backend must first look up an existing `operationId` in its idempotency record and return its stored result for an identical payload. Only a previously unseen operation proceeds to the revision comparison. It must implement an atomic compare-and-set per `(authenticatedServerId, room, playerUuid)`:
1. validate that the authenticated server may manage the supplied server ID and room;
2. persist the policy by server, room, and participant UUID together with the highest accepted revision and operation ID;
3. accept a mutation only when its revision is strictly greater than the stored revision, then atomically persist the revision, operation ID, and desired mute state before applying LiveKit effects;
4. reject a lower revision without changing the stored policy or LiveKit permissions, even if that HTTP request arrives after a newer mutation;
5. if the same operation ID and revision are received again with the identical payload, return the original successful result without applying a second logical mutation; reject reuse with a different payload;
6. update connected participants so `canPublish=false` when muted and restore the intended permission when unmuted;
7. apply persisted policy to future tokens, including after reconnects.

The revision check and policy write must be one atomic database transaction or equivalent compare-and-set operation. Do not implement the check as a separate read followed by an unconditional write. The current plugin stores its revision counter in the local Paper configuration, so each `serverId` must identify one installation with a single active writer. If multiple active instances share one `serverId`, they must use a shared revision allocator; independent local counters are not sufficient.

### Timeout and unknown outcomes

A client timeout does not prove that the backend rejected or rolled back a mutation. The plugin therefore does not reuse an operation revision: the next command allocates a larger, durably stored revision and uses a new operation ID. If the older request is still running or arrives late, the backend must reject it after a higher revision has been accepted. If the older request actually committed first, the later higher revision supersedes it. Retrying the *same logical operation* must reuse its exact operation ID, revision, and payload; never retry it with a new operation ID at the same revision.

The per-player queue in the plugin only serializes local dispatch. The backend compare-and-set is the authority that prevents stale remote writes across timeouts, retries, reconnects, and multiple plugin instances.

A client-side `forceMuted` hint is not enforcement. The LiveKit participant permission is the enforcement boundary. If the participant is offline, persist the policy and ensure the next token has publishing disabled.

The plugin currently invalidates its local active/pending session state when `mcraftvoice.use` is revoked, but the upstream protocol has no server-to-client disable action and this repository does not yet call a backend participant-removal/admission-control operation for that event. An already connected LiveKit participant may therefore remain connected until the client disconnects or its session ends. Do not treat local permission revalidation as immediate remote revocation.

## Operations

Use one backend credential per Paper server, keep LiveKit API credentials only in the backend secret store, restrict administrative capability to required rooms, and test token issuance, mute, unmute, reconnect, and restart against a non-production room before deployment.
