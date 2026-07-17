# Rhymo — Phase 1 Product & UI Plan

## Product vision

Rhymo is a mobile-first music discovery app that makes finding a new favorite song feel as immediate as scrolling short-form video. The core interaction is a vertical, full-screen song feed: swipe up for the next track, tap to pause, save what you love, and open the artist or song details without leaving the flow.

## Free-product guarantee

Rhymo is a completely free experience. There will be no Premium tier, subscription, paywall, locked audio quality, paid downloads, exclusive paid feature, or upgrade prompt. Every listener gets the same product capabilities. If operating costs later require monetization, it must be designed separately without restricting core listening features.

## Phase 1 goal

Build a polished, interactive Android UI prototype that validates the product experience before connecting production services.

### Included

- Welcome/onboarding screen with a clear value proposition
- Google sign-in UI and a guest preview path
- Home discovery screen with personalized greeting, search, trending topics, and new releases
- Search screen with recent searches, genre filters, and live local filtering
- Full-screen vertical swipe player with play/pause, like, save, share, progress, and song metadata
- Library screen with favorites, downloads, and playlists entry points
- Responsive dark visual system, edge-to-edge layout, accessible touch targets, and visible navigation state

### Deferred to Phase 2

- Real Google OAuth and account persistence
- Production music catalog/search API
- Audio streaming, background playback, media controls, and downloads
- Synced likes, playlists, history, and recommendations
- Sharing deep links and artist/song detail pages

## Primary user journey

1. The listener sees the value of Rhymo and signs in with Google or previews as a guest.
2. Home immediately presents trending moods and fresh tracks.
3. Search helps the listener find a song, artist, album, or genre with minimal typing.
4. Selecting a track opens the vertical player.
5. Swiping up changes the song; lightweight actions keep the listener immersed.
6. Saved music remains easy to reach from Library.

## Information architecture

- **Home:** discovery, trends, latest releases, jump back into the swipe feed
- **Search:** query, filters, recent searches, results
- **Swipe:** immersive vertical song feed and primary playback controls
- **Library:** liked songs, downloads, playlists, listening history

## Experience principles

- **Music first:** artwork and current-track context dominate the player.
- **One-thumb friendly:** high-frequency actions live near the bottom/right edge.
- **Fast to understand:** every screen has one obvious primary action.
- **Continuous discovery:** entering and leaving the swipe feed never feels like a dead end.
- **Calm, not crowded:** strong hierarchy, generous spacing, and only essential metadata.
- **Accessible by default:** readable contrast, 48dp targets, clear selected states, and text labels where icons may be ambiguous.

## Visual direction

- Near-black ink background with warm off-white type
- Electric lime as the primary action/accent color
- Coral and violet secondary accents for editorial energy
- Large editorial headlines paired with compact metadata
- Soft rounded cards, subtle borders, layered gradients, and restrained motion

## Success checks for Phase 1

- A new user can identify the app's purpose and enter it in under 10 seconds.
- Search, navigation, and vertical track switching are discoverable without instruction.
- The prototype has useful empty/default states and no dead-end buttons for primary actions.
- UI builds cleanly on the current Android project and supports common phone sizes.

## Suggested roadmap

### Phase 2 — Functional foundation

Google authentication, catalog provider, audio engine, local persistence, and backend data model.

### Phase 3 — Personalization & social

Recommendation signals, collaborative playlists, follows, comments/reactions, and shareable song moments.

### Phase 4 — Quality & launch

Offline behavior, performance, accessibility audit, analytics, music-rights compliance, QA, and store release.

## Production technology roadmap

Dependencies are added only when their feature enters active development, keeping APK size, startup time, and maintenance risk controlled.

### Already active

- Jetpack Compose and Material 3 for UI, responsive navigation, theming, and animation
- Kotlin Coroutines for asynchronous authentication and UI work
- Firebase Authentication with Android Credential Manager
- Media3 ExoPlayer hosted in a MediaSessionService for real streaming, background playback, lock-screen controls, and MediaStyle notifications

### Phase 2 — Playback and real data

- Navigation Compose for typed screen navigation and deep links
- Licensed catalog integration to replace the current public test stream
- Coil for album and artist artwork
- Retrofit, OkHttp, and Kotlin Serialization for catalog APIs
- Room plus Flow for likes, history, playlists, and offline metadata
- Hilt for dependency injection once repositories and services are separated
- Paging 3 for large search and discovery feeds

### Phase 3 — Delight and discovery

- Lottie for selected loading, success, empty, and like states
- Shared transitions for artwork-to-player navigation
- Palette extraction for artwork-aware player backgrounds
- Photo Picker for profile images
- SpeechRecognizer for voice search
- Modal bottom sheets for queue, lyrics, and sleep timer
- Vico for listening statistics

### Add only when justified

- Haze/blur after performance testing on low-end Android devices
- MotionLayout only for interactions that Compose animation cannot express cleanly
- Maps Compose only when nearby concerts/events are backed by real data
- Audio waveform rendering only when waveform data or live recording exists
- Firebase Analytics, Crashlytics, FCM, App Check, and Remote Config as their corresponding production features launch

Accompanist is not a default dependency: many older utilities are now available directly in Compose or Android APIs. A focused module may use it only when the platform stack has no maintained equivalent.
