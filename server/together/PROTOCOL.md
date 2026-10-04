# Shiny Together protocol (v1)

One WebSocket per member at `wss://<server>/v1/rooms/<CODE>/socket`. Frames are JSON text; `t` is the type.
Codes are six characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no 0/O/1/I), case-insensitive.

## HTTP

| Request | Answer |
|---|---|
| `POST /v1/rooms` | `201 {code, hostKey, invite}` — a new session; `hostKey` makes the first hello the host |
| `GET /v1/rooms/<CODE>` | `{code, host, listeners, approval, playing:{title, artist, thumbnail}}` or 404 |
| `GET /j/<CODE>` | the invite page (opens `shinymusic://listen?code=` on Android) |
| `GET /v1/health` | `{ok, protocol, time}` |

## Joining

Client → `hello {name, device, mode:"phone"|"remote", token?, hostKey?}`

- `token` (from an earlier `welcome`) resumes the same member, host status included — no approval.
- `hostKey` (from `POST /v1/rooms`) makes you the host.
- Otherwise, with approval `"ask"`, you get `waiting {host, hostPresent}` and the host gets `request {id, name}`;
  the host answers `approve {id}` or `deny {id, block?}`. With approval `"open"` you're let in at once.

Server → `welcome {you:{id, token}, room, s}` where `s` is server time and `room` is:

```
{code, hostId, settings:{approval, guestsCanAdd, guestsCanControl, votesReorder},
 members:[{id, name, mode, connected, joinedAt}], playback, queue, votes, skip, chat, requests}
```

Refusals: `denied {reason:"declined"|"blocked"|"full"}`, `error {code:"no_room"|"ended"}`; the socket then closes with 44xx.

## Clock

`ping {c}` → `pong {c, s}`. The client keeps the offset `s − (c + rtt/2)` from the round trip with the least
delay. Every playback position is stated at a moment of server time, so any phone can work out where the room is.

## Playback (the host conducts)

- Host → `playback {track, playing, buffering, positionMs, at, rate}`: the room was at `positionMs` at server time
  `at`, advancing at `rate` while `playing && !buffering`. Sent on every change and every 5 s while playing.
- Host → `queue {items:[{track, addedBy:{id,name}?}]}`: what plays after the current song, in order.
- Everyone else receives `playback {playback}` / `queue {queue}`.

A track is `{id, title, artist, durationMs, album?, thumbnail?, artistId?, explicit?, local?}`; `id` is a YouTube
video id. `local` marks a file only the host has (its id is then a stand-in).

## Everyone

| Client → server | Effect |
|---|---|
| `add {track, next}` | Needs `guestsCanAdd` (or host). Shown to all at once as a `pending` queue item; the host gets `add {uid, track, addedBy, next}` and puts it in its player; the adder gets `added`. |
| `remove {uid}` | Host, or whoever added it. The host gets `remove {uid, trackId, index}`. |
| `vote {trackId, on}` | `votes {votes:{trackId:[memberIds]}}` to all; the host reorders when `votesReorder`. |
| `skip {on}` | Skip vote on the current song. Needs `max(2, ceil(connected/2))`; then the host gets `control {action:"next", reason:"vote"}` and all get `skip {skip, passed:true}`. |
| `control {action, positionMs?}` | `play`/`pause`/`seek`/`next`/`prev`, needs `guestsCanControl`; relayed to the host as `control {…, from}`. |
| `react {emoji}` | One of 🔥 ❤️ 😂 😍 👏 🎉 🥹 💃; relayed to all as `react {emoji, from, at}`. |
| `chat {text, replyTo?}` | ≤ 500 chars; `chat {message}` to all; the last 60 are kept for joiners. |
| `mode {mode}`, `rename {name}` | Updates the member; `room {…}` to all. |
| `leave` | You leave; if you hosted, the longest-present member becomes host (`notice {kind:"host_changed"}`). |

## Host only

`approve`, `deny`, `kick {id, block}`, `host {id}` (hand over), `settings {approval?, guestsCanAdd?,
guestsCanControl?, votesReorder?}` (opening the door lets everyone waiting in), `end` (everyone gets `ended`).

## Upkeep

A host who drops is given 45 s to come back before the room is handed on. Members away 10 min are forgotten. An empty
room is deleted after 15 min; an ended one after 1 min.
