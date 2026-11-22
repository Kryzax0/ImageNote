# ImageNote

Quick voice notes written straight onto your photos. Built for the Galaxy Z Fold 7.

Open the app and your recent photos appear in a side-scrolling reel. Pick one, tap the mic and speak.
When you stop talking for 1.5 seconds, your words are written on the image in white
handwriting with a black outline, slightly italic, on a transparent background.

## Features

- **Recent-photo reel** that updates automatically when you take a photo or screenshot.
  "Browse" opens the system photo picker for older images.
- **Voice to text on the image.** Recording starts the instant you tap: audio is buffered while
  the speech recogniser warms up, so your first word isn't cut off. The note finishes after a
  pause you choose (1–6 s, default 1.5 s).
  Live text appears on the photo as you speak. It uses on-device recognition when available, so it
  works offline. Tap the mic again to finish early.
- **Edit notes right on the photo.** Tap a note to select it, then:
  - drag to move it, with orange guide lines that snap to the centre;
  - pinch to resize and twist to rotate (snaps to 0°/45°/90°);
  - drag the orange corner handle to resize and rotate with one finger;
  - tap the red ✕ corner to delete it.
- **Floating toolbar** on the selected note: Edit, Add speech (dictate more words onto the note),
  Smaller, Bigger, Center, Straighten, Duplicate, Bring to front, Send to back, Lock and Delete.
- Double-tap or long-press a note to edit its text. Long-press empty space, or tap **Type**, to type
  a note instead of speaking it. **Hold: original** shows the photo without notes.
- Undo and redo.
- **Style options.** 12 handwriting fonts plus sans, serif and mono, with size, italic slant,
  outline thickness, opacity, text and outline colours (swatches or a custom colour picker), bold,
  ALL CAPS, alignment, letter and line spacing, wrap width, and an effect: none, soft shadow,
  neon glow, highlighter, dim box or solid box. Save favourite styles
  as presets. Changes apply to the selected note, or to new notes when none is selected.
- **Notes are kept per photo**, so you can come back and keep working. The Notes tab lists the
  text with copy, edit and delete.
- **Save and share.** A full-resolution copy goes to `Pictures/ImageNote`. The original is never
  changed. The spoken text is also stored in the photo's description (EXIF), so it is searchable.

### Transparent mode

Tap **Transparent** (top-right of the editor) and ImageNote shrinks to a small floating bar.
Use your phone normally (other apps, websites, maps…) and tap the orange mic on any screen:

1. Recording starts immediately. The bar hides itself and ImageNote captures what's on screen.
2. ImageNote switches back to normal mode with that screenshot open and is **already listening**.
   Your words appear live on the screenshot.
3. After the pause the note is placed, and you can edit everything as usual: drag, resize,
   restyle, add more, save or share.

- Drag the bar up or down before tapping to choose the height where the first note goes.
- Captures are saved to `Pictures/ImageNote/Screens` and appear first in the reel.
- Transparent mode stays available. Leave the app and the bar is back, without asking to share
  the screen again. Exit with ✕ on the bar, "Stop" in the notification, or "Transparent: ON" in
  the app.
- The bar hides while ImageNote itself is open.
- The first time, Android asks for: microphone, "Display over other apps", notifications (optional)
  and screen capture. It always captures the entire screen; there is no single-app option.

### Fold 7 specific

| Posture | Layout |
|---|---|
| Cover screen | Compact layout: image, reel and a big mic button. The style panel opens as a sheet. |
| Unfolded | Image and reel on the left, with a permanent Style / Notes / Settings panel on the right. |
| Flex mode (half-folded on a table) | Image on the top half; reel and a larger mic on the bottom half, so you can talk hands-free. |
| Half-folded like a book | The panes split exactly at the hinge. |

- Folding or unfolding keeps your state, even while you're speaking.
- Split screen and pop-up view: drag a photo from Samsung Gallery into ImageNote to open it. After
  saving, long-press the "Saved" chip and drag the result into another app (Notes, Messages…).
- "Share" or "Open with" from Samsung Gallery sends a photo straight into ImageNote.
- Dark theme with fluorescent orange accents.

## Install

Download `ImageNote.apk` on the phone, tap it, and allow "Install unknown apps" for your browser or
Files app when asked.

## Build

Requirements: JDK 17+ and the Android SDK (platform 36).

```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest # renderer + layout screenshot tests (Robolectric)
```

The release build is signed with the debug key so it installs directly for personal use.

## Credits

The handwriting fonts in `app/src/main/res/font` come from Google Fonts and are used under the
SIL Open Font License 1.1 (Caveat, Dancing Script, Pacifico, Kalam, Indie Flower, Great Vibes,
Patrick Hand, Sacramento, Architects Daughter) and the Apache License 2.0 (Satisfy,
Permanent Marker, Homemade Apple).
