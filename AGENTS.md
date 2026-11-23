# AGENTS.md

## Default requirements for all Git apps

Apply these requirements whenever you create or modify an app maintained in Git. For new app repositories, include these requirements in the repository's root `AGENTS.md` so they travel with the source and apply when work runs on another computer or in Codex Cloud.

- Provide separate country/region and language choices. On first launch, use the device's country/region when available and its preferred language when supported. Preserve the user's explicit choices across launches and offer a way to return to the device defaults.
- Include Korean, English, Japanese, Chinese, Spanish, and Russian in every release. Match the device's supported language even when there is no exact regional variant. Use English when the device language is unsupported and for any missing translation.
- Translate the app interface, settings, short app description, usage guide, and update screens into all six languages. Follow the selected app language consistently, including icon accessibility labels.
- Keep the Settings gear, About (information) icon, and How to Use (help) icon easy to find from the main app screen. About shows a short description. How to Use shows the steps needed to get started.
- Inside the Settings gear, include these entries in order: How to Use (사용법), About (설명), Language Settings (언어 설정), and Update (업데이트). Put the country/region choice inside Language Settings. Give Update its own icon and connect it to the in-app update flow.

## In-app updates for all apps

- Every app must offer a way to check for updates inside the app and start the update from there. Use the platform's supported update mechanism and show the installation prompt when required.
- For Android APKs distributed through `by-jsk/app-downloads`, publish the signed APK and an app-specific `updates/<app-id>.json` with `versionCode`, `versionName`, `apk`, and `notes`. Use a direct HTTPS APK link that works without signing in. The app must compare `versionCode` with the installed version before offering an update.
- Keep the package ID and signing key consistent across releases. Before publishing, verify that a previous version can detect, download, and install the update without losing app data. Keep the public README download link in sync with the update metadata.
