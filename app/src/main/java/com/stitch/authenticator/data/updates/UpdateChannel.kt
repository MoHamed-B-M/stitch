package com.stitch.authenticator.data.updates

/**
 * Where an update is looked for.
 *
 * Both channels serve the same CI-signed APK; they only decide how adventurous a check is.
 * [STABLE] follows the tagged releases (`v1.2.3`) this repository publishes when a version is cut
 * from `main`. [BETA] follows the `v-next` pre-release, which the repository replaces on every
 * push to `dev` — the newest build of the app, straight from CI.
 *
 * Stored as the ordinal in the preferences file, the same way
 * [com.stitch.authenticator.data.settings.ThemeMode] is stored.
 */
enum class UpdateChannel {
  STABLE,
  BETA,
}
