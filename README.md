# Rhymo

Rhymo is a free Android music-discovery app built around an immersive vertical swipe player. Listeners can search for music, move between tracks like a short-form feed, save songs, build playlists, follow artists, view synchronized lyrics, and join song conversations.

The project is currently a functional prototype. It uses an unofficial catalog API and must move to a licensed music provider before a production release.

## Highlights

- Firebase Google authentication with a guest-preview path
- Home discovery feed with real mood-based searches and trending tracks
- Debounced song and artist search with loading, empty, retry, and offline states
- Full-screen vertical swipe player powered by Media3/ExoPlayer
- Preloaded playback queue, background playback, seek controls, and media notification metadata
- Tap to play/pause, double-tap to like, and swipe up for the next track
- Artwork-aware control colors using Android Palette
- System-controlled light and dark themes; no manual theme toggle
- Responsive phone/tablet navigation and predictable Android back behavior
- Persistent liked songs, saved songs, offline downloads, and custom playlists
- Song sharing through the Android share sheet
- **Listen Together:** share a Rhymo room link and play one song in sync with a friend anywhere
- Synchronized lyrics with highlighted playback position and lyric-line sharing
- Song reactions and comments with like, edit, and delete actions
- Artist follow/unfollow state and a following list on Profile
- Reusable Compose music-list components and action menus

Rhymo has no Premium tier, subscription, locked audio quality, paid downloads, or upgrade prompts. See [FREE_PRODUCT_POLICY.md](FREE_PRODUCT_POLICY.md).

## Current feature status

| Area | Status | Notes |
| --- | --- | --- |
| Google authentication | Functional | Firebase Auth + Android Credential Manager |
| Listen Together | Functional after Firestore setup | Host controls play, pause, and seek; guests automatically catch up |
| Guest mode | Functional | Does not require sign-in |
| Search and catalog | Prototype | Uses the unofficial Saavn API |
| Streaming playback | Functional prototype | Media3 queue and background service |
| Likes, saves, downloads | Functional | Stored on the device |
| Playlists | Functional | Create, rename, delete, and add/remove tracks |
| Lyrics | Functional when available | Timed/plain lyrics from LRCLIB |
| Comments and reactions | Functional locally | Not shared between users or devices yet |
| Artist following | Functional locally | Stored on the device |
| Deep links | Functional | `rhymo://listen?room=…` joins a shared listening room |
| Production music licensing | Required | Must be completed before distribution |

## Tech stack

- Kotlin
- Jetpack Compose and Material 3
- Kotlin Coroutines and Flow
- Media3 ExoPlayer and `MediaSessionService`
- Firebase Authentication
- Cloud Firestore for real-time Listen Together room state
- Android Credential Manager and Google ID
- Retrofit with Gson
- Coil 3
- Android Palette
- SharedPreferences for prototype local persistence
- JUnit and Android Lint

## Requirements

- Android Studio with the Android SDK installed
- Android Studio's bundled JDK or another Java 11-compatible runtime
- Android SDK matching the project configuration
- Android 7.0/API 24 or newer device/emulator
- Internet access for authentication, search, artwork, lyrics, and streaming
- A Firebase Android configuration for Google sign-in

Project configuration:

- Application ID: `com.rhymo.music`
- Minimum SDK: 24
- Target SDK: 36
- Compile SDK: 36.1

## Getting started

### 1. Clone the project

```bash
git clone https://github.com/codervivek5/Rhymo.git
cd Rhymo
```

### 2. Configure Firebase

Rhymo expects the Firebase Android app package to be `com.rhymo.music`.

1. Create or open a Firebase project.
2. Register an Android app with package name `com.rhymo.music`.
3. Run the signing report:

   ```bash
   ./gradlew signingReport
   ```

4. Add the debug SHA-1 and SHA-256 fingerprints in Firebase Project settings.
5. Enable **Google** under Authentication → Sign-in method.
6. Download the refreshed `google-services.json`.
7. Place it at `app/google-services.json`.
8. Sync Gradle and rebuild the app.

For **Listen Together**, also create a Cloud Firestore database and publish the authenticated rules in [FIREBASE_LISTEN_TOGETHER.md](FIREBASE_LISTEN_TOGETHER.md). Google sign-in is required for that production-safe rule set.

For Play Store releases, also add the Play App Signing SHA fingerprints. Full instructions and recommended Firebase services are documented in [FIREBASE_SETUP.md](FIREBASE_SETUP.md).

### 3. Build and verify

macOS/Linux:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

If the shell cannot locate Java on macOS but Android Studio is installed:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and run the `app` configuration on an emulator or physical device.

## App navigation

- **Home:** greeting, mood discovery, trending card, fresh tracks, notifications, and profile
- **Search:** remote search with popular suggestions and reusable song rows
- **Swipe player:** vertical queue, playback gestures, lyrics, comments, reactions, follow, save, download, playlist, share, and Listen Together actions
- **Library:** liked songs, offline downloads, saved tracks, and user-created playlists
- **Profile:** followed artists, listening entry points, and sign out

The bottom navigation contains Home, Search, and Library. The immersive player opens above the normal tab layout. Android Back returns from nested destinations/player to the previous app destination before exiting from Home.

## Playback interactions

| Gesture/action | Result |
| --- | --- |
| Single tap on artwork | Play or pause |
| Double tap on artwork | Like the song |
| Swipe up/down | Move through the playback queue |
| Drag seek bar | Seek within the current track |
| Lyrics card | Open synchronized lyrics |
| Comment action | Open reactions and song conversation |
| Three-dot action | Lyrics, recommendations, **Listen together**, download, and playlist options |

### Listen Together

1. Start a song, then open **⋮ → Listen together**.
2. Share the generated Rhymo link with a friend.
3. When they open it in Rhymo, the same track starts at the host's current position.
4. Host play, pause, and seek actions synchronize in real time. Guests can still like, save, comment, and share without interrupting the host.

Each phone streams the track directly from the music provider—Rhymo synchronizes playback state only and never relays audio.

The center play/pause indicator automatically disappears after one second. The player loads the queue into Media3 once and changes tracks after the pager settles, reducing unnecessary rebuffering and UI jank.

## Data and persistence

`SavedMusicStore` persists:

- liked tracks
- saved tracks
- offline-track metadata
- custom playlists

Downloaded audio is stored inside the app's private files directory.

`SocialMusicStore` persists:

- followed artists
- per-song emoji reactions
- comments and comment likes
- edited/deleted comment state

This social state is currently local to one installation. It is intentionally behind a repository boundary so it can later be replaced with Firestore without redesigning the Compose UI.

`ListeningRoomStore` uses Cloud Firestore for temporary, real-time room state. It persists no playback history on the device. See [FIREBASE_LISTEN_TOGETHER.md](FIREBASE_LISTEN_TOGETHER.md) for the rule set and setup steps.

For real multi-user social features, use authenticated Firebase collections such as:

```text
users/{uid}/following/{artistKey}
songs/{songId}/comments/{commentId}
songs/{songId}/reactions/{uid}
```

Public writes must not be enabled without Firestore security rules, App Check, rate limiting, reporting/blocking, and moderation.

## External services

### Saavn API

Rhymo currently uses `https://saavn.sumit.co/` for prototype search, metadata, artwork, streams, and related-track discovery. The documented recommendation endpoint may fail for some valid tracks, so Rhymo falls back to an artist search.

This API is unofficial. Availability, terms, content rights, and stream URLs can change without notice.

### LRCLIB

Rhymo uses `https://lrclib.net/` for synchronized or plain lyrics when a matching record is available. Tracks without a match show a graceful unavailable state.

## Project structure

```text
app/src/main/java/com/rhymo/music/
├── MainActivity.kt                 # Root Compose app and screen orchestration
├── auth/
│   └── GoogleAuthService.kt        # Credential Manager → Firebase Auth
├── data/
│   ├── LyricsRepository.kt         # LRCLIB lookup and timed-LRC parsing
│   ├── MusicRepository.kt          # Catalog contract and demo fallback
│   ├── SaavnMusicRepository.kt     # Search and recommendation mapping
│   ├── SavedMusicStore.kt          # Library, downloads, and playlists
│   ├── SocialMusicStore.kt         # Follows, reactions, and comments
│   ├── ListeningRoomStore.kt        # Firestore-backed real-time playback rooms
│   └── remote/SaavnApi.kt          # Retrofit API models/endpoints
├── model/                          # Song, lyrics, and social models
├── notifications/                 # Notification dialog fragment
├── playback/                       # Media3 controller and service
└── ui/
    ├── components/                 # Reusable song rows and social sheets
    ├── navigation/                 # App destinations
    └── theme/                      # System-aware colors and typography
```

## Troubleshooting

### “No credential available” during Google sign-in

- Confirm a Google account is added to the device.
- Update Google Play services.
- Enable Google sign-in in Firebase Authentication.
- Add the current signing certificate's SHA-1 and SHA-256.
- Download Firebase configuration again after adding fingerprints.
- Confirm `google-services.json` is inside `app/`, then sync and rebuild.

### `default_web_client_id` is missing

The Google Services plugin did not generate the Firebase resources. Confirm `app/google-services.json` exists, matches `com.rhymo.music`, and contains a Web OAuth client.

### Search, artwork, lyrics, or playback is unavailable

- Check the device's network connection.
- Retry with another query or song.
- Remember that the prototype catalog and lyrics providers are external services.
- Do not treat these services as production SLAs.

### Listen Together cannot create or join a room

- Sign in with Google (the recommended Firestore rules require an authenticated Firebase user).
- Create Cloud Firestore in the Firebase project, then publish the rules from [FIREBASE_LISTEN_TOGETHER.md](FIREBASE_LISTEN_TOGETHER.md).
- Confirm the installed app uses the same `google-services.json` Firebase project as the friend who created the link.
- Both listeners still need a working music-stream connection; the room sync does not transfer audio.

### Local library or comments disappeared

Likes, playlists, follows, reactions, and comments are currently stored locally. Clearing app data or uninstalling the app removes them. Offline downloads are also app-private and are deleted with app data.

## Product and engineering docs

- [PRODUCT_UI_PLAN.md](PRODUCT_UI_PLAN.md) — product vision, UX principles, implementation status, and roadmap
- [FIREBASE_SETUP.md](FIREBASE_SETUP.md) — Firebase setup and service recommendations
- [FIREBASE_LISTEN_TOGETHER.md](FIREBASE_LISTEN_TOGETHER.md) — Cloud Firestore room rules and real-time listening setup
- [FREE_PRODUCT_POLICY.md](FREE_PRODUCT_POLICY.md) — permanent free-product constraints

## Production checklist

Before publishing Rhymo:

- replace the unofficial catalog with a licensed provider
- confirm music, artwork, metadata, download, and lyrics rights
- move user/social state to an authenticated backend
- add Firestore rules, App Check, moderation, and abuse controls
- add Crashlytics, privacy-safe analytics, and performance monitoring
- test offline/error behavior across supported Android versions
- complete accessibility, privacy, security, and Play policy reviews
- configure release signing and Play App Signing fingerprints

## Contributing

Keep changes aligned with Rhymo's free-product policy and Compose architecture. Before opening a pull request, run:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Do not commit private signing keys, service-account credentials, production secrets, licensed audio files, or user data.
