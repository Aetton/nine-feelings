# Security

Do not include diary entries, backup files, signing keys or credentials in public reports. For a vulnerability, use the repository host's private security reporting mechanism when available. Public issues should contain only non-sensitive reproduction steps using synthetic data.

The app is offline and uses Android private storage, not a separate encryption layer. Exported JSON backups are not encrypted. An unlocked device, compromised Android installation or an intentionally shared backup can expose journal contents. A backup selected through Android's document picker may be held by an external provider chosen by the user.

Import is size-limited and validated before an atomic transaction. Existing entries are identified by persistent UUIDs; imports do not overwrite local edits. No code is loaded from backups.

Maintain the supported Android versions, Gradle/JDK versions and migration tests when patching. No security audit or certification is claimed.
