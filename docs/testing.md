# Automated and manual integration test plan

## Automated Paper lifecycle integration tests

The Gradle test suite includes MockBukkit-based integration tests. They boot a simulated Paper server, load the actual plugin from its descriptor/resources, verify that the plugin remains enabled when optional backend configuration is absent, and dispatch both `/mcraftvoice status` and its `/mcvoice` alias. These tests run in CI with `./gradlew test`.

MockBukkit is not a real Paper server and does not implement the Minecraft Web Client, a production token backend, or LiveKit/WebRTC. Passing these tests proves only the tested plugin lifecycle and command-registration behavior. Use the manual end-to-end plan below for interoperability validation.

# Manual integration test plan

This checklist describes tests that require a real Paper server, a compatible Minecraft Web Client build, the trusted backend, and a non-production LiveKit room. Passing unit tests does not establish end-to-end compatibility.

## Test environment

- Paper 1.21.11 and Java 21.
- Plugin built with `./gradlew clean test build`.
- A client build that implements `minecraft-web-client:voice-chat`; record its exact version or commit.
- A backend implementing [the token and moderation contract](backend-contract.md).
- A dedicated non-production LiveKit room.
- A test account with `mcraftvoice.use` and an operator account with `mcraftvoice.moderate`.

## Protocol and lifecycle

- Join the server and verify that `available` is sent after the client registers the channel.
- Enable voice chat and verify that `config` contains the expected WSS URL, room, player UUID, hearing range, and short-lived token.
- Verify that the client joins the expected LiveKit room and that audio travels over WebRTC, not Minecraft plugin messages.
- Disable voice chat and verify that the server removes the active session.
- Disconnect while token issuance is pending; verify that no late response creates an active session.
- Reconnect and verify that the player can request a fresh session.
- Remove `mcraftvoice.use` from a connected player while a voice session is active; verify that the server removes its local session on the next proximity update and no further `nearby` updates are sent for that player. The current upstream protocol has no server-to-client disable action; this does not by itself disconnect an already connected LiveKit participant.
- Remove `mcraftvoice.use` while token issuance is pending; verify that the pending request is invalidated and a late response cannot create a session.

## Proximity

- Two players in the same world and inside the configured hearing range appear in each other's `nearby.players`.
- Players outside the range or in different worlds do not appear.
- Moving across the range boundary updates the list.
- Leaving the server removes the player from subsequent proximity lists.
- Unchanged proximity data is not repeatedly transmitted.

## Moderation

- Configure the moderation endpoint and verify that `/mcraftvoice status` reports it as configured.
- Mute a connected player. The backend must persist the policy, revoke publishing permission for the LiveKit participant, and stop existing published audio tracks.
- Verify that the muted UUID appears in other listeners' `nearby.forceMuted` lists.
- Verify that a token requested after mute has `video.canPublish=false`.
- Unmute and verify that the backend restores the intended publishing permission.
- Mute an offline player whose identity is cached by Paper (the player must have joined at least once); restart Paper and verify that the persisted policy is applied when the player reconnects.
- Submit mute then unmute (and unmute then mute) rapidly for the same UUID; verify backend requests are processed in submission order and the local mirror reflects only confirmed operations.
- Submit moderation requests for two different UUIDs; verify they are not unnecessarily serialized with each other.
- Change mute state while token issuance is pending; verify that the plugin discards credentials with stale publish permissions and requests a fresh token.
- Make the moderation endpoint return non-2xx or time out; verify that the plugin does not change its local mute state or report success.

## Invalid responses and security

- Reject token responses with malformed JSON, an unexpected room, an unexpected UUID, an unsupported JWT algorithm, missing grants, expired tokens, or excessive lifetime.
- Reject token backend bodies larger than 64 KiB without parsing or retaining the full response.
- Reject LiveKit URLs that are not WSS, except loopback WS during local development.
- Verify that backend redirects are not followed.
- Send malformed plugin messages repeatedly; verify warning logs are rate-limited per player.
- Send unknown actions containing control characters; verify they are sanitized before logging.
- Confirm that logs never contain the backend Bearer token, LiveKit API secret, or participant JWT.
- Verify that `VoiceCredentials.toString()` redacts the JWT.
- Test Cloud and self-hosted token revocation behavior separately. Self-hosted LiveKit does not invalidate already-issued tokens when permissions change; stale tokens can remain usable until expiry.

## Evidence to record

Record the Paper build, plugin commit, Minecraft Web Client commit/version, backend build, LiveKit deployment type, test room, test date, and pass/fail results. Do not attach real credentials, JWTs, player personal data, or production logs to public issues.
