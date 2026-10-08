# Privacy

*Last updated: 8 October 2026*

Shiny is a music player, not an advertising business. It has no ads and nothing to sell, so it has no reason to collect much. This page says what leaves your phone, when, and where it goes.

## The short version

- You don't need an account to use Shiny.
- Your library, history, downloads and settings live on your phone.
- Nothing is sold or shared for advertising.
- Anything that talks to a server beyond playing music is something you switch on.

## What stays on your phone

Playlists, liked songs, listening history and stats, downloads, lyrics, search history and every setting are stored in Shiny's own storage on your device. Uninstalling Shiny, or clearing its data in Android's settings, removes all of it. A backup is a file you create and keep yourself.

## What Shiny sends, and to whom

### To play music

Shiny is a client for YouTube Music. Searching, browsing and playing send requests from your phone straight to YouTube, as a browser would, and YouTube sees your IP address. If you sign in to your YouTube Music account, those requests carry your account, and [Google's privacy policy](https://policies.google.com/privacy) applies to what Google keeps.

### To show lyrics and artwork

To find lyrics, animated covers and artist images, Shiny sends the song's title, artist and sometimes its length to the service that provides them. These lookups carry no account and no identifier of yours.

### To recognise a song

When you ask Shiny to recognise what's playing, it records a few seconds from the microphone, turns them into an audio fingerprint on your phone, and sends only the fingerprint to the recognition service. The recording itself isn't uploaded or kept. The microphone is used only while you've asked for recognition.

### To check for updates

Shiny asks GitHub whether a newer release exists. You can turn this off in *Settings → About*.

### Crash and playback reports (the `gms` build only)

The `gms` build uses Google's Firebase to send:

- **Crash reports:** what the app was doing when it crashed, with your phone model and Android version.
- **Playback-health events:** that a song failed to start, and how — a timeout, or a refused request. Never which song.

The `foss` build contains no Firebase code and sends neither.

## Things you can switch on

Each of these is off until you choose it, and stops when you disconnect it.

| Feature | What is shared | With |
| :-- | :-- | :-- |
| **Shiny Social** | Your Google sign-in identifies your account. Your profile, your friends list and the song you're playing are stored so friends can see them. | Shiny's servers |
| **Listen Together** | The name you pick, the shared queue, votes, reactions and chat, so everyone in the session stays in step. | Shiny's servers |
| **Shared playlist links** | The playlist's name and songs, so the link can open it. | Shiny's servers |
| **Spotify** | Shiny reads your playlists, Liked Songs and mixes from your account. | Spotify |
| **Discord** | The song you're playing appears as your status. | Discord |
| **ListenBrainz** | The songs you play are scrobbled to your account. | ListenBrainz |

Shiny's servers run on Cloudflare. They hold what the features above need and nothing else: no listening history, no library, no contacts.

## Permissions

| Permission | Why |
| :-- | :-- |
| Music and audio | To play the audio files on your phone |
| Microphone | Song recognition, only while you use it |
| Notifications | Playback controls and download progress |
| Network / SIM country | To pick which country's charts to show. The country never leaves your phone except as part of the chart request to YouTube |

## Children

Shiny isn't directed at children under 13 and doesn't knowingly hold information about them.

## Deleting your data

- **On your phone:** uninstall Shiny or clear its data.
- **On Shiny's servers:** write to the address below from the account you signed in with, and your Social profile and anything attached to it will be deleted.
- **With other services:** disconnect them in Shiny, then use that service's own settings.

## Changes

When this page changes, the date at the top changes with it, and the history is public in this repository.

## Contact

[hello@shinymusic.in](mailto:hello@shinymusic.in), or an [issue on GitHub](https://github.com/lastdaytheOG/Shiny/issues).
