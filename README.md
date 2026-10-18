# stitch

Privacy-first Android authenticator. Fully local TOTP, encrypted on-device storage, no cloud, no accounts, no tracking. Built with Kotlin, Jetpack Compose, and Material 3 Expressive.

## Passkeys

stitch can act as a system passkey provider (FIDO2/WebAuthn): credentials created through the system's credential prompts are stored in the app's encrypted vault with their private keys in the Android Keystore, and every signature requires your biometric or device credential first. Saved passkeys are listed and removable under **Settings → Passkeys**; scanning a `FIDO:/` handle, a `webauthn://` link, or a WebAuthn request QR code in the in-app scanner opens a save sheet instead of an "invalid code" message.

> [!IMPORTANT]
> **To use this app as a Passkey provider for a website (e.g., google.com), the website must explicitly trust this app's SHA-256 signature via a `/.well-known/assetlinks.json` file. Without server-side verification, this app can only be used for local testing or custom personal servers.**
