# F-Droid inclusion request

Status: prepared, not submitted. No F-Droid issue or merge request exists yet.

Title: Nine Feelings (ru.tochka.mood)

## Request text

* [x] The app is FLOSS, with no proprietary runtime dependencies, advertising, tracking, or network permission. Please see the project-age note below.
* [x] No existing request or fdroiddata issue was found by application ID on 2026-11-13.
* [x] The upstream repository includes Fastlane metadata in English and Russian, screenshots, and changelogs.
* [x] This request is made on behalf of the upstream author, who supports inclusion.
* [ ] Optional donation.

### APPLICATION ID

`ru.tochka.mood`

Use the complete build recipe in [fdroid.template.yml](fdroid.template.yml), copied to `metadata/ru.tochka.mood.yml` in fdroiddata.

### Why add this app?

Nine Feelings offers a small, offline emotion diary centered on home-screen widgets. A nine-button grid makes recording an emotion quick; users can customize the vocabulary without rewriting historical records. It does not require an account or an online service.

Project-age note: this is a newly published project and does not yet have three months of public development history. The upstream user has used the app on their Android phone and provided feedback that has been incorporated. We request consideration with that limitation stated explicitly; we do not claim an established user base or satisfaction of the age criterion.

### Summary

Offline emotion diary with customizable home-screen widgets

### Description

Record emotions from a 3-by-3 grid on your home screen. Choose either a 2-by-2 or 3-by-3 widget footprint. After recording, a short confirmation card can open the saved entry for a comment.

Customize the names, symbols, and colors of the nine emotion slots. Existing records retain the emotion values selected at the time, so editing a slot does not change history or statistics.

Browse history, edit comments, and view emotion statistics in the app. Export and import JSON backups using the Android document picker. The interface uses a native dark theme.

All diary data stays on the device unless the user explicitly exports it. There are no accounts, ads, analytics, or network permission. Android 8.0 or later is required.

### Build and validation

* Source tag: `v0.4.1`, commit `387f0c3d62c2277b5726018ee1b7784f6a39ef07`.
* Version name/code: `0.4.1` / `5`.
* License: MIT. Gradle Wrapper is separately licensed under Apache-2.0.
* JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1, compile/target SDK 36, minimum SDK 26.
* No external app runtime dependencies. F-Droid should build from source and sign its own APK; the upstream signing key is not needed.
* Local validation on 2026-11-13 with fdroidserver 2.4.5: `fdroid readmeta` and `fdroid lint ru.tochka.mood` passed with no warnings. `fdroid build -v -l ru.tochka.mood` scanned the source and successfully built an unsigned APK from the public tag. Validation ran in an isolated Linux container with JDK 17 and no upstream signing key. This verifies a local F-Droid build, not acceptance by F-Droid or bit-for-bit reproducibility of the signed upstream APK.
* Upstream CI: release build and 10 instrumentation tests on each of API 26 and API 36 passed. See [the release CI run](https://github.com/Aetton/nine-feelings/actions/runs/43459243401).

### Submission procedure

The current RFP template describes its issues as an automated review starting point and directs actual inclusion submissions to a fdroiddata merge request. Submit the validated recipe to a fork of [fdroiddata](https://gitlab.com/fdroid/fdroiddata), following its CONTRIBUTING instructions. If an initial automated review is useful, use this request text in [RFP](https://gitlab.com/fdroid/rfp/-/issues/new), avoiding duplicate submissions.

Before submitting, recheck for duplicate requests and replace the status above with the real issue or merge-request URL only after submission succeeds.
