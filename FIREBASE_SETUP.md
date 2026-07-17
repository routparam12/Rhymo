# Rhymo Firebase setup

## What is already integrated

- Firebase Authentication SDK via Android BoM 34.15.0
- Android Credential Manager Google account flow
- Google ID token exchange with Firebase Authentication
- Loading, cancellation, configuration-error, and authenticated UI states
- Conditional Google Services plugin: the project still builds before private project configuration is added

## One-time console setup required

1. Create a Firebase project at the Firebase Console.
2. The Android app is registered with package name `com.rhymo.music`.
3. Run `./gradlew signingReport` and add the debug SHA-1 and SHA-256 fingerprints under Project settings → Your apps.
4. Open Authentication → Sign-in method and enable Google.
5. Download the refreshed `google-services.json` and place it at `app/google-services.json` (not the repository root).
6. Rebuild and select **Continue with Google**.

The local app now uses the permanent Firebase application ID `com.rhymo.music`. Before a Play Store release, also add Play App Signing's SHA fingerprints.

## Recommended Firebase services for Rhymo

### Use now

- **Authentication:** Google sign-in and a stable UID for every listener.
- **Cloud Firestore:** user profiles, liked song IDs, playlists, follows, recently played items, and lightweight recommendation signals. Store metadata only—not licensed audio files.
- **Analytics:** measure onboarding completion, search-to-play conversion, skips, saves, session length, and feed retention. Never send raw search text or personal data without an explicit privacy decision.
- **Crashlytics:** production crash reporting and player/auth failure diagnosis.
- **Remote Config:** change feed experiments, onboarding copy, feature flags, and rollout percentages without publishing a new APK.

### Add when the related feature exists

- **Cloud Functions:** trusted playlist counters, notifications, moderation hooks, catalog-provider webhooks, and server-side recommendation jobs.
- **Cloud Messaging:** new-release, followed-artist, playlist collaboration, and re-engagement notifications with user controls.
- **App Check with Play Integrity:** protect Firestore, Storage, and Functions from unofficial clients; enable before public launch.
- **Performance Monitoring:** startup, search request, artwork loading, and playback-start latency.
- **App Distribution:** send QA builds to testers before Play Store releases.

### Use carefully

- **Cloud Storage:** profile pictures or user-generated artwork only. Music streaming requires catalog licensing, protected delivery, and usually a dedicated music provider/CDN; Firebase Storage is not a rights solution.
- **Realtime Database:** useful for presence or live listening rooms, but Firestore is the simpler primary database for Rhymo's normal app data.

## Suggested user document shape

`users/{uid}`: display name, avatar URL, created timestamp, preferences. Keep likes and playlist tracks in subcollections so documents do not grow without limit. Firestore rules must require `request.auth.uid == uid` for private user data.
