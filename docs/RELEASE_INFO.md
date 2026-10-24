# Shiny release notes

The same notes appear in the app under *Settings → About → What's New*. APKs are on the [Releases](https://github.com/lastdaytheOG/Shiny/releases) page.

## 1.2.7

A calmer, roomier queue, and playlists that play all the way through.

**New**
- Redesigned Up Next: one list with the song playing now highlighted, songs you've played above it and what's next below.
- Queue mode gives the list the whole screen on a dark background, with play and pause in the header.
- Every song in the queue has a menu with Play Next, Remove from Queue and Clear upcoming songs, and you can undo a removal or a clear.

**Improved**
- Long playlists now load in full, even with Autoplay off, instead of stopping after 50 songs.
- Shuffle keeps its order when songs are added, and stays in sync as more of a playlist loads.
- Shuffle, Repeat and Autoplay sit together at the top of the queue, with clear labels.
- Long queues show their songs a page at a time, so the queue opens quickly.
- Reorder songs by long-pressing and dragging them.

**Fixed**
- Songs were sometimes skipped or played twice when you started a playlist while it was still loading.
- The queue could show the same songs twice for a moment after you picked a song.

## 1.2.6

A cleaner Home and more dependable playback controls.

**Improved**
- Cleaner Home: the Your Week summary, Surprise Me card, and mood-selection section are gone.
- Improved Shuffle: shuffle behavior and queue synchronization are more reliable.
- Better Repeat Controls: repeat-mode handling is more consistent while tracks finish or change.
- Autoplay Improvements: automatic track selection and playback behavior are more dependable.
- Queue Improvements: playback modes and queue controls stay synchronized.

## 1.2.5

A rebuilt Shiny: a new design on every screen, friends and Discord, and an appearance you can make your own.

**Highlights**
- **A new look, everywhere.** Home, Library, Search, Now Playing and Settings were rebuilt with one design, from the type to the way pages move.
- **Listen with friends.** Add friends, see what they're playing, share a profile page, and let people listen along from your Discord status.
- **Appearance, your way.** Atmosphere, artwork glow, accent colour, material, page transitions and interface size, with a live preview as you change them.

**New**
- Home learns from your very first play.
- Charts follow your country. Pick another country or the global chart in *Settings → Library & Home*.
- Smart Shuffle.
- Music sources: choose which folders count as music. Voice notes, recordings and system sounds are left out.
- Choose a download folder, so downloads stop taking up Shiny's own storage.
- Export songs as MP3, then share them with any app.

**Improved**
- Shiny opens faster, and songs start playing sooner.
- Crossfade waits until the next song is ready, so a transition never stalls part-way.
- Song details only show what a song actually has.
- Privacy, Downloads & Storage and About are simpler and clearer.

**Fixed**
- Lyrics stay in time after you seek.
- Songs you'd already played no longer download again every time Shiny restarts.
- Opening a song from another app no longer gets replaced by your previous queue.
- The first song of a new queue now gets the next one ready in time.
