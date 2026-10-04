// End-to-end check of the Together server: `npm run together:dev`, then `npm run together:test`.
// Plays a host and three guests through a whole session and asserts what each one sees.
import WebSocket from "ws";

const BASE = process.env.TOGETHER_URL ?? "http://127.0.0.1:8788";
const WS_BASE = BASE.replace(/^http/, "ws");
let failures = 0;
let checks = 0;

function check(ok, label) {
  checks++;
  if (ok) console.log(`  ok   ${label}`);
  else {
    failures++;
    console.log(`  FAIL ${label}`);
  }
}

class Client {
  constructor(name) {
    this.name = name;
    this.inbox = [];
    this.waiters = [];
  }
  open(code) {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(`${WS_BASE}/v1/rooms/${code}/socket`);
      this.ws.on("open", resolve);
      this.ws.on("error", reject);
      this.ws.on("message", (data) => {
        const msg = JSON.parse(data.toString());
        this.inbox.push(msg);
        this.waiters = this.waiters.filter((w) => !w(msg));
      });
      this.ws.on("close", (code) => (this.closed = code));
    });
  }
  send(msg) {
    this.ws.send(JSON.stringify(msg));
  }
  /** Resolves with the first message (already received or future) of type [t] that passes [test]. */
  next(t, test = () => true, timeout = 3000) {
    const found = this.inbox.find((m) => m.t === t && test(m));
    if (found) {
      this.inbox.splice(this.inbox.indexOf(found), 1);
      return Promise.resolve(found);
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${this.name}: no ${t} within ${timeout}ms`)), timeout);
      this.waiters.push((msg) => {
        if (msg.t !== t || !test(msg)) return false;
        clearTimeout(timer);
        this.inbox.splice(this.inbox.indexOf(msg), 1);
        resolve(msg);
        return true;
      });
    });
  }
  drain() {
    this.inbox = [];
  }
  close() {
    this.ws.close();
  }
}

const track = (id, title) => ({ id, title, artist: "Artist", durationMs: 200000, thumbnail: "https://i.ytimg.com/vi/x/hq.jpg" });

async function main() {
  console.log(`Together smoke test against ${BASE}`);
  const health = await (await fetch(`${BASE}/v1/health`)).json();
  check(health.ok && health.protocol === 1, "health");

  const created = await (await fetch(`${BASE}/v1/rooms`, { method: "POST" })).json();
  check(/^[A-HJ-NP-Z2-9]{6}$/.test(created.code) && created.hostKey, `create room ${created.code}`);
  const code = created.code;

  const host = new Client("host");
  await host.open(code);
  host.send({ t: "hello", name: "Priya", device: "dev-host", hostKey: created.hostKey, mode: "phone" });
  const hw = await host.next("welcome");
  check(hw.room.hostId === hw.you.id && hw.you.token, "host welcomed as host");

  // Clock sync
  const t0 = Date.now();
  host.send({ t: "ping", c: t0 });
  const pong = await host.next("pong");
  check(pong.c === t0 && Math.abs(pong.s - Date.now()) < 2000, "pong carries client and server time");

  // Preview
  host.send({ t: "playback", track: track("dQw4w9WgXcQ", "First"), playing: true, positionMs: 1000, at: Date.now() });
  await new Promise((r) => setTimeout(r, 150));
  const preview = await (await fetch(`${BASE}/v1/rooms/${code.toLowerCase()}`)).json();
  check(preview.host === "Priya" && preview.playing?.title === "First" && preview.listeners === 1, "preview shows host and song (code case-insensitive)");
  const invite = await (await fetch(`${BASE}/j/${code}`)).text();
  check(invite.includes("Listen with Priya") && invite.includes(code), "invite page");

  // Guest asks to join (approval is "ask" by default)
  const ana = new Client("ana");
  await ana.open(code);
  ana.send({ t: "hello", name: "Ana", device: "dev-ana", mode: "phone" });
  const waiting = await ana.next("waiting");
  check(waiting.host === "Priya", "guest waits for the host");
  const request = await host.next("request");
  check(request.name === "Ana", "host gets the join request");
  host.send({ t: "approve", id: request.id });
  const aw = await ana.next("welcome");
  check(aw.room.playback?.track?.id === "dQw4w9WgXcQ" && aw.room.playback.playing, "approved guest gets live playback");
  check(aw.room.members.length === 2 && aw.room.requests.length === 0, "guest sees two members, no requests");
  const hostRoom = await host.next("room", (m) => m.members.length === 2);
  check(hostRoom.members[1].name === "Ana", "host sees Ana join");

  // Declined guest
  const troll = new Client("troll");
  await troll.open(code);
  troll.send({ t: "hello", name: "Troll", device: "dev-troll" });
  await troll.next("waiting");
  const trollReq = await host.next("request", (m) => m.name === "Troll");
  host.send({ t: "deny", id: trollReq.id, block: true });
  const denied = await troll.next("denied");
  check(denied.reason === "blocked", "host can decline and block");
  const troll2 = new Client("troll2");
  await troll2.open(code);
  troll2.send({ t: "hello", name: "Troll", device: "dev-troll" });
  check((await troll2.next("denied")).reason === "blocked", "blocked device can't ask again");

  // Open the door: next guest walks straight in
  host.send({ t: "settings", approval: "open" });
  await ana.next("room", (m) => m.settings.approval === "open");
  const ben = new Client("ben");
  await ben.open(code);
  ben.send({ t: "hello", name: "Ben", device: "dev-ben", mode: "remote" });
  const bw = await ben.next("welcome");
  check(bw.room.members.find((m) => m.name === "Ben")?.mode === "remote", "open room: guest joins instantly as a remote");

  // Playback relay
  host.send({ t: "playback", track: track("aaaaaaaaaaa", "Second"), playing: true, positionMs: 0, at: Date.now() });
  const ap = await ana.next("playback", (m) => m.playback.track?.id === "aaaaaaaaaaa");
  check(ap.playback.playing, "guests receive the host's playback");

  // Guest adds a song: shows up at once for everyone, host gets the add
  host.send({ t: "queue", items: [{ track: track("bbbbbbbbbbb", "Queued") }] });
  await ana.next("queue", (m) => m.queue.length === 1);
  ana.send({ t: "add", track: track("ccccccccccc", "Ana's pick"), next: true });
  const added = await ana.next("added");
  check(added.track.title === "Ana's pick", "adder gets a confirmation");
  const hostAdd = await host.next("add");
  check(hostAdd.addedBy.name === "Ana" && hostAdd.next === true, "host is told who added what");
  const benQueue = await ben.next("queue", (m) => m.queue.length === 2);
  check(benQueue.queue[0].track.id === "ccccccccccc" && benQueue.queue[0].pending, "pending add appears first for 'play next'");
  // Host applies it and republishes
  host.send({ t: "queue", items: [{ track: track("ccccccccccc", "Ana's pick"), addedBy: hostAdd.addedBy }, { track: track("bbbbbbbbbbb", "Queued") }] });
  const settled = await ben.next("queue", (m) => m.queue.length === 2 && !m.queue[0].pending);
  check(settled.queue[0].addedBy?.name === "Ana", "attribution survives the host's queue");

  // Guests can't control by default
  ana.send({ t: "control", action: "pause" });
  check((await ana.next("error")).code === "not_allowed", "guest control refused when not allowed");
  host.send({ t: "settings", guestsCanControl: true });
  await ana.next("room", (m) => m.settings.guestsCanControl);
  ana.send({ t: "control", action: "seek", positionMs: 42000 });
  const control = await host.next("control");
  check(control.action === "seek" && control.positionMs === 42000 && control.from.name === "Ana", "allowed guest control reaches the host");

  // Votes
  ana.send({ t: "vote", trackId: "bbbbbbbbbbb" });
  const votes = await host.next("votes", (m) => m.votes.bbbbbbbbbbb?.length === 1);
  check(!!votes, "votes are tallied");

  // Reactions and chat
  ben.send({ t: "react", emoji: "🔥" });
  const react = await host.next("react");
  check(react.emoji === "🔥" && react.from.name === "Ben", "reactions reach everyone");
  ben.send({ t: "react", emoji: "💩" });
  ana.send({ t: "chat", text: "  this one!  " });
  const chat = await ben.next("chat");
  check(chat.message.text === "this one!" && chat.message.from.name === "Ana", "chat is trimmed and relayed");

  // Skip vote: 3 connected → needs 2
  host.drain();
  ana.send({ t: "skip" });
  const skip1 = await ben.next("skip", (m) => m.skip.voters.length === 1);
  check(skip1.skip.needed === 2, "skip needs 2 of 3");
  ben.send({ t: "skip" });
  const passed = await ana.next("skip", (m) => m.passed);
  check(!!passed, "skip vote passes");
  const hostSkip = await host.next("control", (m) => m.action === "next");
  check(hostSkip.reason === "vote", "host is told to skip");

  // Mode change
  ana.send({ t: "mode", mode: "remote" });
  const modeRoom = await host.next("room", (m) => m.members.find((x) => x.name === "Ana")?.mode === "remote");
  check(!!modeRoom, "listening mode is shared");

  // Reconnect with a token keeps identity
  ben.close();
  await host.next("room", (m) => m.members.find((x) => x.name === "Ben")?.connected === false);
  const ben2 = new Client("ben2");
  await ben2.open(code);
  ben2.send({ t: "hello", name: "Ben", device: "dev-ben", token: bw.you.token });
  const bw2 = await ben2.next("welcome");
  check(bw2.you.id === bw.you.id, "token resumes the same member");

  // Host transfer, then kick
  host.send({ t: "host", id: aw.you.id });
  const transferred = await ana.next("room", (m) => m.hostId === aw.you.id);
  check(!!transferred, "host can hand over");
  ana.send({ t: "kick", id: bw.you.id });
  const removed = await ben2.next("removed");
  check(removed.blocked === false, "new host can remove someone");

  // Old host leaves; the new host ends it
  host.send({ t: "leave" });
  await ana.next("room", (m) => m.members.length === 1);
  check(true, "leaving updates the room");
  ana.send({ t: "end" });
  const ended = await ana.next("ended");
  check(ended.reason === "host", "host ends the session");
  await new Promise((r) => setTimeout(r, 200));
  const gone = await fetch(`${BASE}/v1/rooms/${code}`);
  check(gone.status === 404, "ended session is gone");

  // Unknown code
  const lost = new Client("lost");
  await lost.open("ZZZZZZ");
  lost.send({ t: "hello", name: "Lost" });
  const err = await lost.next("error");
  check(err.code === "no_room", "unknown code is explained");

  console.log(`\n${checks - failures}/${checks} checks passed`);
  process.exit(failures ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
