# Plan: Stitch Authenticator

## Phase 1 — Foundation ✅ (complete, CI green)
- **Goal:** Set up an empty, version-pinned Android Compose app (Material 3 + Kotlin + CI) — first runnable release.
- **Delivered:** AGP 9.4.0 / Gradle wrapper 9.6.0 / Kotlin 2.4.21 / JDK 17 / minSdk 26 / targetSdk 36 / `compileSdk { version = release(37) }`; Compose BOM 2026.09.00 (UI 1.12.x, animation 1.12.1, material3 1.5.0-beta01); `StitchMotion` spring tokens and the `bouncySpring()`/`pressScale()`/`bouncyContentSize()` helpers; `app/build.gradle.kts` strips no-op `tween(` if accidentally introduced (guard lint); `.github/workflows/ci.yml` + `release.yml`; strict MIT `LICENSE`; `.gitignore`; baseline `MainActivity` + empty Compose UI. Released `v1.0.0` via CI.
- **Verification:** local motion check (zero `tween(` in `app/src`), CI green → release published. **Status: ✅ GREEN**.

## Phase 2 — Encrypted Local Storage ✅ (complete, CI green)
- **Goal:** Keep secrets off disk in plain text.
- **Delivered:** `androidx.security:security-crypto:1.1.0-alpha07` encrypted `SharedPreferences` (AES256-GCM) for `keys.xml`; `TokenStorage` interface + `EncryptedPreferencesTokenStorage`; `AppGraph` DI (singletons); unit tests with `InMemoryTokenStorage`. Commit-only flow; no raw secret strings printed.
- **Verification:** 18 unit tests green on CI. **Status: ✅ GREEN**.

## Phase 3 — TOTP Engine ✅ (complete, CI green)
- **Goal:** RFC 6238 TOTP: HMAC-SHA1 (with optional SHA256/SHA512), period, digits 6/8, time step.
- **Delivered:** `OtpAlgorithm` (SHA1/SHA256/SHA512), `TotpEngine` (clean, testable), Base32 decode (RFC 4648, skips hyphens/whitespace, folds case). Pure Kotlin — no Android dependencies. Period clamped 1..3600, digits restricted to 6–8. 17 unit tests (time skew ±30s, edge cases, percent-only decode, invalid padding).
- **Verification:** CI green. **Status: ✅ GREEN**.

## Phase 4 — Domain Model + Repository ✅ (complete, CI green)
- **Goal:** Minimal model + persistence seam.
- **Delivered:** `Token` data class (`id`, `issuer`, `accountName`, `secret`, `algorithm`, `digits`, `period`, `createdAt`, `lastUsedAt`), `TokenRepository` (add/update/delete/list/get, cold-cache-only on first read from storage to avoid re-serialising), `TokenCodec` (JSON + Base64) for storage payloads. Migrated from raw prefs to encoded list. 30 unit tests cover encode/decode round-trip, corruption fallback, delete order, update semantics.
- **Verification:** CI green. **Status: ✅ GREEN**.

## Phase 5 — Add Account Flow ✅ (complete, CI green)
- **Goal:** Add an account manually or by pasting an `otpauth://` URI.
- **Delivered:** Manual + Link segmented modes; strict `OtpAuthUri` parser: absent algorithm = SHA-1 (RFC 4226/TOTP default), percent-only decoding (no `+`/form unescaping for secret query values), issuer dedupe via `subtitle` (don't duplicate if already in label), `SupportedDigits = setOf(6,8)`; one shared draft (no split state), deterministic validation; `AddAccountViewModel` + `AddAccountScreen` with Material 3 components and spring-friendly transitions; `TokenListScreen` (main) with countdown ring (uses `StitchMotion.Smooth`, not `tween`) and subtle bouncy placement; `Outcome` (Parsed/Rejected). 19 tests added → **84 unit tests** total, all green.
- **Verification:** CI green (run `39063937022`). **Status: ✅ GREEN**.

## Phase 5.5 — Settings (interlude) ✅ (complete, CI green)
- **Goal:** Preferences, theme, dynamic color, biometric lock.
- **Delivered:** DataStore (`androidx.datastore:datastore-preferences:1.1.1`) — keys `theme_mode` (0=system/1/light/2/dark), `dynamic_color` (bool), `biometric_lock` (bool); `SettingsDataStore`, `SettingsRepository` (test-friendly); `BiometricHelper` over `BiometricPrompt` (enable and disable both confirmed biometrically); `SettingsScreen` with grouped `Card`s (Appearance / Security / About), segmented theme row; `MainActivity` is `FragmentActivity` (required for BiometricPrompt); gear icon in main top bar. 13 tests → **97 unit tests** total. CI green (run `39075655011`, Dev build #18).
- **Verification:** CI green. **Status: ✅ GREEN**.

## Phase 7 — In-App Updates ✅ (complete, CI green)
- **Goal:** Respect privacy (no Play Core) — user-initiated checks for Stable (`/releases/latest`) and Beta (`v-next` pre-release) from GitHub Releases. Ship unsigned/CI-signed APKs; install via FileProvider + `REQUEST_INSTALL_PACKAGES` (with user confirmation).
- **Delivered:** Zero new external dependencies (`kotlinx-serialization-json` already present): `UpdateChannel` (Stable=0, Beta=1), `UpdateVersions` (semantic-ish compare: major.minor.patch, ignore prerelease/build metadata; never downgrade; build-number compare for Beta by parsing `Dev build #M` from `v-next` title, falling back gracefully), `GitHubRelease` model + `GitHubUpdateChecker` (`HttpURLConnection`), `ApkDownloader` (ranges, temp file, sha256 check placeholder hook), `UpdatesViewModel`, `UpdatesCard` in Settings, `update_channel` persisted in DataStore, FileProvider in manifest; 30 tests → **114 unit tests** total. CI green (run `39114720738`, **Dev build #20**).
- **Notes:** LinearWavyProgressIndicator + `Button(shapes = ...)` verified for material3 1.5.0-beta01; no authored `tween(`; install only when user taps. No background polling.
- **Verification:** CI green. **Status: ✅ GREEN**.

## Phase 5b — QR Scanning & Add Menu ✅ (complete, CI green)
- **Goal:** Add-account FAB becomes a speed dial (Manual / Link / Scan QR); scan `otpauth://` QR codes on-device; small torch toggle; 1.5 s same-QR debounce.
- **Delivered:** `AddAccount` becomes `data class(mode, initialLink)` with `@Serializable` enum; `ScanQr` NavKey; Navigation wires replace-on-scan (pop scanner, push pre-filled Link form) so no accidental re-scan on back. Main FAB: ExtendedFAB rotates plus→× (`bouncySpring`), label swaps with `bouncyContentSize`, options rise on `fadeIn(Smooth)+slideInVertically(bouncySpring)` with scrim covering content (Box) + `BackHandler` closes menu first; hand-drawn Link/Scan/QR glyphs (core set has no Link/QR/Flash). CameraX 1.6.2 + ML Kit **bundled** barcode-scanning 17.3.0 (on-device, no Play Services, ~2.4 MB model); `QrScannerScreen`: permission flow with rationale + "Enter key instead"; preview + `ImageAnalysis` `STRATEGY_KEEP_ONLY_LATEST`, only QR codes; torch toggle driven by camera's own `torchState` (single observer), viewfinder frame; 1.5 s same-code cooldown + one-shot navigation guard; snackbar for non-otpauth. Seeding via ViewModel constructor (`initialMode`, `initialLink`) runs through the same paste/validation pipeline. `abiFilters` trimmed to `arm64-v8a` and `armeabi-v7a` (drops x86/x86_64, saves ~11.7 MB).
- **Fix-loop:** corrected `rememberSaveable` to `runtime.saveable`, `arcTo` flag to `isMoreThanHalf`, scanner's `ImageVector` import (`graphics.vector`). 
- **Verification:** CI green (runs `39118984698`, `39121759534`) — **Dev build #23**, **115 unit tests** (+1 seeding test). No authored `tween(` in app/src. **Status: ✅ GREEN**.

## Phase 6 — Polish & Reduced Motion (queued after Phase 5b)
- **Goal:** Honour `MotionScheme.expressive()` tokens fully; strip any remaining `tween(`; harden reduced-motion paths; tiny a11y nits.
- **Planned:** audit spring usage (keep 0.5–0.7 damping where `bouncySpring` fits), add a small `@Preview` for speed dial + scanner rationale, test "Enter key instead" path, ensure countdown ring doesn't flicker; optional: migrate `actions/setup-java@v4` → `v5` in CI (deprecation warnings already visible).
- **Entry:** only once 5b is stable on `dev`. **Status: ⏳ QUEUED**.

## Phase 7: FIDO2/WebAuthn Passkey Integration ✅ (complete, CI green — 7.1–7.5)

### Phase 7.1: Dependencies, Manifest, & Database
**Goal:** Prepare the foundation for Passkey cryptography and storage.
- **Dependencies:** Add `androidx.credentials:credentials:1.3.0` and `androidx.credentials:credentials-play-services-auth:1.3.0`.
- **Manifest:**
  - Ensure `<uses-permission android:name="android.permission.USE_BIOMETRIC" />` (already present for Phase 5.5 — verify and keep).
  - Register `PasskeyProviderService` with `androidx.credentials.CredentialProviderService` action and `android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE`.
- **Database:** Extend Room schema with `PasskeyEntity` (or separate encrypted storage). Fields: `credentialId` (String, primary), `rpId` (String), `username` (String), `userHandle` (String), `publicKey` (String), `keyAlias` (String, Android Keystore reference). DAO: `insert`, `getByRp(rpId)`, `getAll`, `deleteByCredentialId`. Use existing encrypted storage context (don't introduce plaintext). Migrations if Room already exists.

**Verification:** Gradle resolves deps, manifest merges cleanly, storage layer compiles. **Status: ✅ GREEN** (run `39124541190`, commit `d027713`, **Dev build #25**, **132 unit tests**).

**Delivered (7.1):** Both artifacts in the version catalog (`credentials = "1.3.0"`). Service registered with the *framework* contract — action `android.service.credentials.CredentialProviderService` (the task's `androidx.credentials.CredentialProviderService` is a class name, not an action; the system binds only the framework string, verified against AOSP `CredentialProviderService.SERVICE_INTERFACE` and the official credential-provider guide), meta-data `android.credentials.provider` → `res/xml/credential_provider` declaring capability `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL` (the constant genuinely lives in androidx, so the mixed prefixes are correct). `PasskeyProviderService` implements all three abstract requests, each answered exactly once with the matching `*UnsupportedException` until 7.3. **Room deliberately skipped** (it would add an annotation processor to a build that has none; encrypted prefs already keep data at rest): `PasskeyEntity` (the six spec fields, `@Serializable`) + `PasskeyCodec` + `PasskeyStorage` seam + `EncryptedPasskeyStorage` (same Keystore master key as the tokens) + `PasskeyRepository` carrying the DAO method set — `insert` (upsert by credentialId), `getByRp`, `getAll`, `deleteByCredentialId` — with Mutex-serialized persist-before-publish and cold-cache reads, plus `AppGraph.passkeyRepository()`. 17 new tests (11 repository + 6 codec). Note: the phase's first CI attempt failed on the `CredentialProviderService` import — in 1.3.0 the class moved to `androidx.credentials.provider` (verified by inspecting the published AAR).

### Phase 7.2: The Cryptographic Engine & Hardware Keystore
**Goal:** Secure EC P-256 key generation and signing.
- **Key generation:** `PasskeyCryptoManager` using `KeyPairGenerator.getInstance("EC","AndroidKeyStore")`, `ECGenParameterSpec("secp256r1")`.
- **Constraints:** `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`, `setDigests(AlgorithmParameterSpec.PURPOSE_SIGN, "SHA-256")` as appropriate; `setUserAuthenticationValidityDurationSeconds(-1)` (strong biometric). Never export private key.
- **Signing:** sign `clientDataHash` via `Signature.getInstance("SHA256withECDSA")` using Keystore private key. Return DER/RAW as required by WebAuthn.
- **Testability:** interface + in-memory fake for JVM tests.

**Verification:** CI green; no Keystore access on JVM unit tests. **Status: ✅ GREEN** (run `39127382741`, commits `cf32a3c` + fixes `c462df9`/`45dfb87`, **Dev build #28**, **145 unit tests**).

**Delivered (7.2):** `PasskeyCryptoManager` interface (`generateKeyPair` / `prepareSignature` / `deleteKey`) + `AndroidPasskeyCryptoManager` (AndroidKeyStore, `PURPOSE_SIGN`, `secp256r1`, SHA-256 digests, auth-per-use via validity `-1` below API 30 / `setUserAuthenticationParameters(0, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` on 30+, `setInvalidatedByBiometricEnrollment(true)` on 30+; private keys never leave hardware; aliases `stitch-passkey-<credentialId>`); `PasskeyCose` hand-rolled CBOR for the five-entry COSE_Key map `{1:2, 3:-7, -1:1, -2:x, -3:y}` (75 bytes, coordinates forced to the curve's 32-byte width); `PasskeySignature` strict ASN.1 DER → raw `r||s` for the JSON `signature` field; `InMemoryPasskeyCryptoManager` JVM fake (real SunEC keys, no Keystore) ready for 7.3/7.4 tests. 13 tests (4 exact-byte COSE vectors, 5 DER edge cases incl. sign-byte strip / minimisation / real round-trip / malformed rejections, 4 contract). CI fix-loop: Kotlin 2.4 rejects the `and` infix on nullable `Int?`; one missing `assertTrue` import.

### Phase 7.3: The CredentialProviderService
**Goal:** Be the system's passkey provider for create/get.
- `PasskeyProviderService : CredentialProviderService()` — minimal implementation, follow androidx.credentials 1.3.0 contracts.
- **onBeginCreateCredentialRequest:** parse `CreatePublicKeyCredentialRequest`. Generate keypair (7.2), persist `PasskeyEntity`, assemble `CreatePublicKeyCredentialResponse` (include `publicKey`, `attestationObject` shape per spec; prefer self-attestation minimal or none per constraints — implement per 1.3.0 response builders).
- **onBeginGetCredentialRequest:** filter by `rpId`, present biometric prompt (reuse `BiometricHelper` where possible), on success sign challenge with matching key, return `PublicKeyCredential` response.
- **Errors:** map cancellations/exceptions to `CredentialProviderService` error responses; no crashes.

**Delivered (7.3):** `PasskeyProviderService` answers every request exactly once behind an `AtomicBoolean` guard (cancellation → `*CredentialInterruptedException`), offering a `CreateEntry` / `PublicKeyCredentialEntry` into the new `PasskeyCredentialActivity` (manifest-registered `exported=false`, reached only through the provider's own mutable PendingIntents). Get filters by `rpId` + `allowCredentials` and returns an *empty* response when nothing matches, so the provider stays off the selector; clear-state acknowledges without touching the user-managed vault (a site must never silently delete entries the user manages in Settings). `PasskeyWebAuthn` — new, kotlinx-serialization-based (the androidx `webauthn` parsers are `@RestrictTo(LIBRARY)` and hardcode `authenticatorSelection`) — owns request parsing, base64url, `clientDataJSON`, §6.1 authenticator data (attestation UP|UV|AT, assertion UP|UV, signCount 0), the `none`-format CBOR attestation object, and both response JSONs. The activity gates create behind a `BiometricPrompt` and get behind `BiometricPrompt.CryptoObject(Signature)` so the auth-per-use Keystore key can only sign for a verified user (DER → raw via `PasskeySignature.toRaw`); every cancel/error path sets the proper `*CancellationException` / `*UnknownException` instead of hanging the selector. Caller origin = `android:apk-key-hash:<sha256>` derived from the signing info. 10 wire-format tests (155 total).

**Verification:** CI green; service visible via manifest; mapping covered by unit tests. **Status: ✅ GREEN** (run `39130810981`, commits `b4407f4` + `c047817` + `7a9b0a0` + `bd9f4db`, **Dev build #32**, **155 unit tests**). CI fix-loop lessons: `android.os.OutcomeReceiver`'s success method is `onResult` (not `onSuccess` — `onError` is a `default` method, which hid this); the final create request's JSON comes from casting `callingRequest` to `CreatePublicKeyCredentialRequest`; `.` is illegal in backticked JVM test names; W3C UV flag = 0x04 (bit 2 — 0x02 is RFU), and attestation carries UP|UV|AT because the flow verifies the user.

### Phase 7.4: UI & QR Scanner Integration (M3 Expressive)
**Goal:** Create/manage passkeys from the app.
- **QR scanner:** detect `FIDO:/`, `webauthn:`, or WebAuthn `Create`/`Get` deep link/QR JSON — route to passkey creation flow (don't treat as "Invalid OTP"). Keep 1.5 s same-code debounce.
- **Creation UI:** M3 Expressive `ModalBottomSheet` "Save Passkey for [Domain]?" with `Spring.DampingRatioMediumBouncy` on successful save+auth; show issuer/domain, username. Use `StitchMotion` tokens (springs only).
- **Settings:** new "Passkeys" group listing saved passkeys (`ListItem` with `rpId` + `username`), swipe-to-delete or trailing delete icon with spring bounce on deletion; empty state.
- **Consistency:** honor reduced motion (if system reduced motion, skip bouncy extras).

**Delivered (7.4):** `PasskeyQr` (new, pure) classifies payloads — `FIDO:/` handles (first dot-bearing segment after the scheme colon), `webauthn://` links (host), WebAuthn request JSON (`rp.id`/`rpId`, routed only when a challenge/rp marker keeps random JSON out) — and the scanner's frame reporter hands back `ScannedCodeKind {OtpAuth, Passkey, Other}` under the existing 1.5 s cooldown and one-shot `handled` guard. The passkey path swaps `ScanQr` for the new nav3 key `PasskeySave(payload)`: an M3 `ModalBottomSheet` (`PasskeySaveScreen` + `PasskeySaveViewModel`) with an editable domain guess + account name, a full-width save that swaps itself for a bouncy "Passkey saved" (`StitchMotion.BouncyQuick`/`Press`) and springs `sheetState.hide()` down before a single-fire guarded pop (swipe-dismiss and saved-effect can never double-pop); saving mints the Keystore key through `AndroidPasskeyCryptoManager` and upserts `PasskeyEntity`, so the provider can offer it immediately. Settings gained `PasskeysSection` in the `SettingsSection` card idiom: rpId over username, trailing `Icons.Filled.Delete` removing record **and** Keystore key via `PasskeysViewModel`, empty state keeps the section's shape (both previews updated). Reduced motion now resolves the *scheme*: `StitchAuthenticatorTheme(motionScheme = null)` (the default) picks `MotionScheme.standard()` when the system animation scales are 0, else `expressive()`. 9 new `PasskeyQrTest` tests.

**Verification:** CI green; scanner routing correct; sheets + list behave with springs; zero `tween(`. **Status: ✅ GREEN** (run `39134672862`, commits `64f841e` + `093c742` + `0820d1b`, **Dev build #36**, **164 unit tests**). CI fix-loop lessons: material3 1.5.0-beta01 redid `MotionScheme` — `expressive()`/`standard()` take *no* parameters (reduced motion = swap the scheme, not a flag); `Icons.AutoMirrored.Filled.Delete` does not exist (a bin does not mirror — `Icons.Filled.Delete` is core); `FIDO:/host/…` has a single slash, so rpId extraction splits after the scheme colon instead of skipping two separators.

### Phase 7.5: CI/CD, Documentation & Asset Links
**Goal:** Ship safely and document limitations.
- **ProGuard/R8:** rules to keep WebAuthn/credential models (`kotlinx.serialization` or data classes) intact; keep `PasskeyProviderService` and `androidx.credentials` reflection-safe where needed.
- **README.md:** add prominent disclaimer: *"To use this app as a Passkey provider for a website (e.g., google.com), the website must explicitly trust this app's SHA-256 signature via a `/.well-known/assetlinks.json` file. Without server-side verification, this app can only be used for local testing or custom personal servers."*
- **CI:** push, watch run until green. Fix Kotlin/compiler/credential API issues. Add minimal unit tests for crypto/DB mapping (no instrumented credential flows in unit tests). Update `PLAN.md` status.

**Delivered (7.5):** `app/proguard-rules.pro` replaces the placeholder with the prepared (inert — release still ships `isMinifyEnabled = false`, and the file says so) R8 configuration: `*Annotation`/`InnerClasses`/`EnclosingMethod` attributes, `$$serializer` keeps, and `@kotlinx.serialization.Serializable` companion/`serializer()`/serial-name keeps scoped to `com.stitch.authenticator.**` — plus documented *non*-rules: manifest-registered components (service + activity) are kept by AGP defaults, `androidx.credentials`/biometricprompt resolve nothing through user-code reflection and ship their own consumer rules (kotlinx-serialization 1.11.0 included), and Keystore key aliases are runtime data. `README.md` grew a Passkeys section whose `> [!IMPORTANT]` callout carries the assetlinks.json disclaimer verbatim and notes the QR save flow. Crypto/DB mapping tests already covered by the 7.1–7.4 suites.

**Verification:** CI green (164 unit tests), APK builds. **Status: ✅ GREEN** (run `39135806634`, commit `502a49a`, docs run `39135240257`).

## Phase 8 — Full-Screen Update Page (queued after Phase 7.x)

- **Goal:** Move updates out of the Settings card into a dedicated full-screen `UpdateScreen.kt` (Google Play / ViVi Music style), Material 3 Expressive throughout.
- **Spec (user task):**
  - `Scaffold`: `TopAppBar` with back arrow left and a centered `SystemUpdate` icon (hand-drawn `ImageVector` — the core icon set has no such glyph and icons-extended stays excluded).
  - Center-aligned content, three visual states:
    - **Checking:** headline "Checking for update…", body "Please wait while we check for the latest features.", *indeterminate* `LinearWavyProgressIndicator` below the headline.
    - **Up to date:** headline "Your app is up to date", body listing current version, build number, last check time (no progress bar).
    - **Update available / installing:** headline "Installing system update…", body with new version + size (MB), *determinate* `LinearWavyProgressIndicator` bound to download progress, release notes below as a `•` bulleted list.
  - Full-width bottom action button (`Scaffold.bottomBar`) with animated `ButtonDefaults.shapes()`; label per state: "Check for update" / "Pause" (during download) / "Install".
  - Refactor `UpdatesViewModel` into explicit states `Checking`, `UpToDate`, `Available`, `Downloading`, `Installing`; keep GitHub-only, user-initiated checks (no background polling).
  - `AnimatedContent` with `StitchMotion` springs between states (springs only, no authored `tween(`).
  - Navigation: the Settings "System update" item opens this screen (new NavKey).
**Delivered (8):** `ui/update/UpdateScreen.kt` — `Scaffold` + `TopAppBar` (back arrow, centered hand-drawn `SystemUpdateIcon`: a tray and a rising arrow in two filled polygons, `material-icons-extended` still excluded); centre states on `AnimatedContent` springs (Idle, Checking with indeterminate wavy bar under the headline, UpToDate listing version + build + "last checked", the offer with `•`-bulleted notes, determinate wavy bar bound to download progress and only shown once bytes move, Unavailable); full-width `bottomBar` `Button` with `ButtonDefaults.shapes()` — labels exactly "Check for update" / "Pause" / "Install". `UpdatesViewModel` refactored to explicit `Checking`, `UpToDate(checkedAtMillis)`, `Available`, `Downloading`, `Installing` (was `Downloaded`), with the offer's size/notes carried through every snapshot; new `pause()` cancels the stream and returns to the offer (restart from zero — documented, no byte-range resume); `setChannel` now also cancels an in-flight stream; GitHub asset `size` decodes (`GitHubAsset.sizeBytes`) into the offer. Settings: card deleted (`UpdatesCard.kt` removed), an Updates section with a "System update" row (`SettingsNavRow`, renamed from `AboutRow`) opens the new `SystemUpdate` NavKey. Honest deviations: the not-yet-downloading offer headlines "Update available" (the spec's "Installing system update…" covers Downloading/Installing), and the Install label on the offer *begins* the download. Post-green fix: `AnimatedContent` is now keyed with `contentKey = { it::class }` — each progress tick minted a fresh `Downloading(...)` (per buffer chunk, hundreds a download) and the instance-keyed container re-ran the cross-fade over a visually identical block every time, flickering the headline and the wavy bar; same kind keeps the slot alive so only the live instance flows in, and only a real state change (offer in, checking, download done) runs the springs — verified against the androidx `contentKey`/`createChildTransition` sources rather than assumed. Tests: UpToDate pins its timestamp, Downloading/Installing pin size + notes, new pause and mid-download channel-switch tests, checker fixture carries a size and pins the absent→0 default.

**Verification:** CI green; zero authored `tween(`; **166 unit tests**. **Status: ✅ GREEN** (run `39139472840`, commits `5e1199a` + `9ac74cb`, **Dev build #41**). CI fix-loop lesson: `materialPath` in material-icons-core takes **no `fill` parameter** — it fills black by default and the `Icon` tint colors the glyph.

## Phase 9 — Settings Redesign: Grouped Rounded Lists (queued after Phase 8)

- **Goal:** Replace the per-item card soup with Google Play–style segmented groups.
- **Spec (user task):**
  - Large, bold expressive "Settings" header at the top (`LazyColumn` layout overall).
  - Reusable `SettingsGroup` composable: a `Column` inside a `Surface`/`Card` with `RoundedCornerShape(24.dp)`; screen background contrasts with the groups (`surface` background, `surfaceContainerHighest`/`surfaceVariant` group surfaces).
  - Reusable `SettingsItem`: leading icon (Palette → appearance, Shield → privacy, etc.), headline, supporting text, trailing control; `HorizontalDivider` between items *inside* a group only — never at a group's top or bottom edge.
  - Groups: (1) **System update** → opens the Phase 8 `UpdateScreen`; (2) **Appearance** — theme selection (System/Light/Dark) + Dynamic Color toggle; (3) **Privacy** — Biometric Lock toggle; (4) **About** — About App, About Me, GitHub. The Passkeys group from 7.4 folds into this layout.
  - Motion: tapping a group item scales down and springs back (`Modifier.interactionSource` + spring, i.e. the existing `pressScale` token); switch/segmented-theme transitions stay spring-driven.
**Delivered (9):** `SettingsGroup` composable — a primary-colour label over one `Surface` (`RoundedCornerShape(24.dp)`, `surfaceContainerHighest` against the screen's `surface`) of `SettingsItem` rows; the group owns the `HorizontalDivider`s and draws them *between* items only, so a line can never touch the top or bottom edge (structurally enforced, not call-site discipline), and `bouncyContentSize` still springs it when rows come and go. `SettingsItem` — leading icon, headline, optional supporting slot, trailing control (auto-chevron when the row opens something, handed-in switch when it *is* the setting); every action row presses with `springClickable` → the house `pressScale` token through its own `InteractionSource`, while trailing controls keep their own gesture so a flip never also fires the row (the pre-existing biometric pattern, now everywhere); the passkey delete icon carries `pressScale` too. Large bold header: `displaySmall` + `FontWeight.Bold` as the first `LazyColumn` item, TopAppBar title emptied to a back-arrow-only bar. Groups in spec order: **System update** (row opens the Phase 8 page), **Appearance** (theme + Dynamic color), **Privacy** (renamed from Security; biometric row + footnote = one item), **Passkeys** (folds in), **About** (About Stitch, About Me [renamed from Developer, dialog included], GitHub, then the Privacy policy and Licenses rows earlier phases shipped). `SettingsIcons.kt`: five hand-drawn glyphs — sun, palette (ring with winding-cancelled hole + three dots), shield (reversed inner subpath = hole, no even-odd flag), text lines, `</>` — because the core set lacks them and `material-icons-extended` stays excluded; `materialPath` is still called with **no named arguments** (the Phase 8 lesson).

**Honest deviations:** the segmented theme selector sits full-width *under* its row's text instead of in a trailing slot — squeezed beside an icon it would starve the headline at 360dp; the Theme row itself has no row-click (its control is the selector beneath — a tap that did nothing would be a lie); the About group keeps the Privacy policy and Licenses rows beyond the spec's named three.

**Verification:** CI green on the first run; zero authored `tween(`; **166 unit tests** keep passing (layout-only phase — nothing new to pin). **Status: ✅ GREEN** (run `39142613300`, commit `476450b`, **Dev build #43**).

## Notes on Execution
- No local Gradle builds. Everything via `.github/workflows/`.
- Per-phase: implement → motion check (grep `tween(`) → commit → `git push origin HEAD:dev` (Windows git against UNC if WSL push hangs) → `gh run watch --interval 30` → fix until green → report.
- Keep damping 0.5–0.7 for bouncy springs; use `bouncySpring`/`StitchMotion.Smooth`/`pressScale`/`bouncyContentSize`. `MotionScheme.expressive()` when available.
- Don't exceed 1–2 concurrent CI runs.
- Privacy first: private keys stay in Android Keystore; biometric gating for get/create; no telemetry.
