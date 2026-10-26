# Next release

Not published yet. When the next version goes out, copy the notes below into
`ReleaseNotes.kt`, `docs/RELEASE_INFO.md` and the website's What's New, then clear this file.

## Release notes

**New**
- Top Songs in Listening Stats opens the full list, with how many times you played each song.
- Autoplay keeps going without a connection, using your downloaded and on-device songs, same artist first.

**Improved**
- Downloaded songs keep their covers, even offline and after a phone cleaner empties storage.
- Less battery use while music plays with a Shiny widget on the home screen.
- Simpler Settings: shorter text in Downloads & Storage, Library & Home, Now Playing and Playback, and Advanced is hidden.

**Fixed**
- Song counts on Home and in Library now match the Liked and Downloaded pages.
- Signing in to YouTube no longer starts over on some phones.
- Lists say "1 song" instead of "1 songs".

## Causes and fixes

Pushed to `main` on 26 October 2026. Checked on the emulator; not yet tested on a phone.

| Problem | Cause | Fix | Checked | Commit |
|---|---|---|---|---|
| Downloaded songs lose their cover, even when searched again | Covers were kept only in Android's cache folder, which the system and phone-cleaner apps empty. Offline there was no way to get them back. | Each downloaded song keeps its own cover file (about 0.2–0.5 MB) in app storage next to the audio, used whenever the normal load fails. Songs downloaded earlier get their cover saved on the next app start with internet. | Wiped the image cache, went offline, restarted: every cover shows. Before the fix, the same case showed blank squares. | `1faeb41` |
| Downloads show no queue, even with Autoplay on | Autoplay could only ask YouTube for more songs, so offline nothing came. Online it already worked. | With no connection (or for a song on the phone), Autoplay continues with the downloaded and on-device songs, same artist first. | Offline, a one-song Liked list plus Autoplay added the other downloaded songs. | `ee8aac8` |
| Song counts on Home sometimes wrong | Counts ignored the "Hide explicit" and "Hide video songs" settings the pages use, and Home did not refresh when songs were added to or removed from a playlist. | Counts follow the same settings as the pages; Home refreshes on downloads, phone songs and playlist changes separately. | Downloaded a third song: Library, the Home tile and the Downloaded page all showed 3 without restarting. | `fc5c7d2` |
| YouTube sign-in goes back on its own on some phones | The sign-in screen restarted from scratch, wiping its cookies, whenever the app's window was rebuilt: rotation, a dark-mode switch, split-screen, or Android killing the page or the app in the background. That is why it only happens on some phones. | Cookies are cleared only when a sign-in starts. After a rebuild it reopens the page you were on, cookies are saved after every page, and a crashed web page is rebuilt instead of closing the app. | Rotated twice mid sign-in; it stayed on the same page. | `77e5173` |
| Performance and battery | While music played, the home-screen widget refreshed 5 times a second, even with the screen off. With the playlist widget placed, every refresh also re-read all liked songs, downloads and playlists. The queue was also written to storage every 10 seconds. | The widget refreshes once a second, only while the screen is on, and reuses its playlist list between song changes. The queue is saved every 30 seconds while playing, and once after pausing. | Work per minute while playing: widget refreshes 300 → 60 (screen on) and 300 → 0 (screen off); queue saves 8 → 2. The emulator has no widget, so no battery difference was measured there. | `9637bd6` |
| Top Songs only played the first song | The Top Songs heading started playback instead of opening anything. | It opens a Top Songs page: every song with rank and "2 plays · artist", with the same Week / Month / Year / All Time switch. | Yes | `c468197` |
| Hide Advanced completely | Asked for. | Hidden from Settings and from Settings search. Its two settings keep their current values. | Yes | `38fb81d` |
| Too much text in Downloads & Storage, Library & Home, Now Playing and Playback | Long footers, and a subtitle under nearly every row. | Text only: shorter footers, subtitles removed where the title already says it. No option removed, same layout. | Screenshots checked | `d48dbde` |
| "1 songs" in lists | The song count had no singular form. | "1 song" and "1 song, 5 minutes". | Yes | `6572489` |
