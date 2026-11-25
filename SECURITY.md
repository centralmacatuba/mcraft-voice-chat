# Security Policy

## Supported versions

Security fixes are applied to the latest maintained version of Mcraft Voice Chat.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Older releases and development snapshots | Best effort; upgrade to the latest release before requesting a fix |

## Reporting a vulnerability

Please report suspected security vulnerabilities privately.

1. Open the repository's **Security** tab.
2. Select **Advisories** and use **Report a vulnerability** if private vulnerability reporting is available.
3. Include the affected version or commit, the impact, reproduction steps, and any relevant logs or proof of concept.
4. Allow the maintainers reasonable time to investigate and prepare a fix before making the issue public.

If private vulnerability reporting is unavailable, contact the maintainers privately through GitHub. **Do not** publish an issue, discussion, exploit, token, credential, or other sensitive information before coordinating disclosure.

You will receive an acknowledgement when the report is reviewed. The maintainers will coordinate follow-up questions, remediation, and disclosure with the reporter.

## Scope

This policy covers security issues in this repository, including:

- the Paper plugin and its plugin-message protocol handling;
- token-backend HTTP requests and LiveKit token handling;
- player identity, permissions, moderation, and session lifecycle;
- build configuration and GitHub Actions workflows.

The security of independently operated Minecraft servers, third-party token backends, LiveKit deployments, hosting providers, and client modifications is outside the direct control of this project. Reports involving these components are still welcome when they demonstrate a vulnerability in Mcraft Voice Chat or a concrete integration risk.

## Sensitive information

Never include real LiveKit API secrets, backend bearer tokens, player personal data, or production credentials in public reports or test fixtures. Revoke and rotate any credential that may have been exposed.
