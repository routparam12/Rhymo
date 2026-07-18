# Listen Together — Firebase setup

Rhymo uses **Cloud Firestore** for a one-song, real-time listening room. The host writes the current play/pause state and playback position; every guest subscribes to the same room document and catches up using the latest timestamp.

## Enable it once

1. Open the existing `rhymo-aeefd` Firebase project.
2. In **Build → Firestore Database**, create a database in the region closest to most listeners.
3. Keep `app/google-services.json` in the app module (it is already present in this project).
4. Build and run the app. Firebase BoM provides the Firestore SDK automatically.

For testing with Google-authenticated users, use these Firestore rules. They allow people with a room link to join it, while allowing transport changes only from the room host.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /listeningRooms/{roomId} {
      allow create: if request.auth != null
        && request.resource.data.hostId == request.auth.uid;
      allow read: if request.auth != null;
      allow update: if request.auth != null
        && request.auth.uid == resource.data.hostId
        && request.resource.data.diff(resource.data).affectedKeys()
          .hasOnly(['isPlaying', 'positionMs', 'updatedAtEpochMs', 'revision']);
      allow delete: if false;
    }
  }
}
```

## Important production note

The first UI phase still supports **Preview as guest**. Firestore rules above require authentication, so use Google sign-in before hosting or joining a room. If guest sharing is required later, enable **Anonymous Authentication** in Firebase and create an anonymous Firebase user for guest sessions; do not open Firestore rules publicly.

## User flow

1. Open any song and use **⋮ → Listen together**.
2. Rhymo creates a room and opens Android's share sheet with a `rhymo://listen?room=…` link.
3. The recipient opens the link in Rhymo. The song loads at the host's current position.
4. The host controls play, pause and seek. Guests stay synced and see the host status chip.

Rooms are not an audio relay: every listener streams the original track from the music provider on their own device. That keeps bandwidth and licensing responsibility with the stream source.
