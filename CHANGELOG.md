# Changelog

## 0.4.1 — 2026-11-13

- Comment editor starts at one line and grows with input, up to eight visible lines.

## 0.4.0 — 2026-11-13

- Standard Gradle wrapper builds debug APK, unsigned/signed release APK and AAB.
- Android 16 target (API 36), minimum Android 8 (API 26).
- Manual JSON backup and atomic, validated, idempotent import.
- Persistent entry UUIDs; non-destructive migration from schema versions 1 and 2.
- History pagination (50 records per page), database-side statistics.
- Entry and emotion-editor drafts survive Activity recreation.
- Widget writes run off the main thread; independent confirmation timers.
- Larger in-app touch targets, accessibility button roles and explicit Android backup exclusions.
- MIT license, privacy documentation, device tests, Linux build recipe and CI.

## 0.3.0

Native dark theme for app, dialogs and widgets.

## 0.2.0

Editable emotion slots, immutable historical emotion revisions, short widget confirmations and entry-specific comment shortcut.

## 0.1.0

Local emotion journal, history, comments, statistics and 2×2 / 3×3 widgets.
