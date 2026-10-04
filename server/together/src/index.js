import { DurableObject } from "cloudflare:workers";
import { invitePage, homePage } from "./pages.js";

/**
 * Shiny Together — the room server.
 *
 * Every session is one Durable Object named by its code. The host's phone is the conductor:
 * it plays the music and tells the room what is playing and what is next. Everyone else
 * either plays along in sync on their own phone or uses theirs as a remote. The room keeps
 * the part that has to be shared — who is here, who may do what, the queue with who added
 * each song, votes, reactions and chat — and relays the rest.
 *
 * Protocol (JSON text frames, `t` is the type) is documented in PROTOCOL.md next to this file.
 */

const PROTOCOL = 1;

// Six characters that survive being read aloud: no 0/O, no 1/I.
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 6;
const CODE_RE = /^[A-HJ-NP-Z2-9]{6}$/;

const MAX_MEMBERS = 32;
const MAX_QUEUE = 200;
const MAX_CHAT = 60;
const HOST_GRACE_MS = 45_000;
const MEMBER_FORGET_MS = 10 * 60_000;
const EMPTY_ROOM_TTL_MS = 15 * 60_000;
const PENDING_ADD_TTL_MS = 8_000;
const REACTIONS = ["🔥", "❤️", "😂", "😍", "👏", "🎉", "🥹", "💃"];
const MODES = ["phone", "remote"];
const APPROVALS = ["ask", "open"];

const TRACK_ID_RE = /^[A-Za-z0-9_\-.:]{1,64}$/;
const IMAGE_RE = /^https:\/\/[^\s"'<>]{1,500}$/;

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.code, message: error.message }, error.status);
      console.error(error);
      return json({ error: "internal", message: "Something went wrong" }, 500);
    }
  },
};

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method;
  if (method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });

  if (path === "/" && method === "GET") return html(homePage(env));
  if (path === "/v1/health") return json({ ok: true, protocol: PROTOCOL, time: Date.now() });
  if (path === "/v1/rooms" && method === "POST") return createRoom(url, env);

  let match;
  if ((match = path.match(/^\/v1\/rooms\/([A-Za-z0-9]+)$/)) && method === "GET") {
    const code = normalizeCode(match[1]);
    const res = await roomStub(env, code).fetch("https://room/preview");
    return res.ok ? json(await res.json()) : json({ error: "not_found", message: "No session with that code" }, 404);
  }
  if ((match = path.match(/^\/v1\/rooms\/([A-Za-z0-9]+)\/socket$/))) {
    if (request.headers.get("Upgrade") !== "websocket") return json({ error: "upgrade_required" }, 426);
    const code = normalizeCode(match[1]);
    return roomStub(env, code).fetch(new Request(`https://room/socket?code=${code}`, request));
  }
  if ((match = path.match(/^\/j\/([A-Za-z0-9]+)$/)) && method === "GET") {
    const code = normalizeCode(match[1]);
    const res = await roomStub(env, code).fetch("https://room/preview");
    const preview = res.ok ? await res.json() : null;
    return html(invitePage(code, preview, url.origin, env));
  }
  return json({ error: "not_found", message: "Not found" }, 404);
}

function normalizeCode(raw) {
  const code = String(raw).toUpperCase();
  if (!CODE_RE.test(code)) throw new HttpError(404, "not_found", "No session with that code");
  return code;
}

function roomStub(env, code) {
  return env.ROOMS.get(env.ROOMS.idFromName(code));
}

async function createRoom(url, env) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const code = randomCode();
    const res = await roomStub(env, code).fetch(`https://room/claim?code=${code}`, { method: "POST" });
    if (res.status === 409) continue;
    if (!res.ok) throw new HttpError(503, "unavailable", "Couldn't start a session");
    const { hostKey } = await res.json();
    return json({ code, hostKey, invite: `${url.origin}/j/${code}`, protocol: PROTOCOL }, 201);
  }
  throw new HttpError(503, "busy", "Couldn't find a free session code");
}

// ---------------------------------------------------------------------------------------
// The room
// ---------------------------------------------------------------------------------------

export class TogetherRoom extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    /** Loaded lazily: after hibernation the object wakes with nothing in memory. */
    this.room = undefined;
    /** Per-member rate limit buckets; losing them on hibernation only forgives a burst. */
    this.buckets = new Map();
  }

  async load() {
    if (this.room === undefined) this.room = (await this.ctx.storage.get("room")) ?? null;
    return this.room;
  }

  async save() {
    await this.ctx.storage.put("room", this.room);
  }

  async fetch(request) {
    const url = new URL(request.url);
    await this.load();
    if (url.pathname === "/claim") return this.claim(url.searchParams.get("code"));
    if (url.pathname === "/preview") {
      if (!this.room || this.room.ended) return json({ error: "not_found" }, 404);
      return json(this.preview());
    }
    if (url.pathname === "/socket") {
      if (!this.room || this.room.ended) {
        // Accept and explain, so the app can say "that session has ended" rather than a
        // generic failure.
        const pair = new WebSocketPair();
        pair[1].accept();
        pair[1].send(JSON.stringify({ t: "error", code: this.room?.ended ? "ended" : "no_room", message: "No session with that code" }));
        pair[1].close(4404, "no room");
        return new Response(null, { status: 101, webSocket: pair[0] });
      }
      const pair = new WebSocketPair();
      this.ctx.acceptWebSocket(pair[1]);
      pair[1].serializeAttachment({ memberId: null, pendingId: null, connectedAt: Date.now() });
      return new Response(null, { status: 101, webSocket: pair[0] });
    }
    return json({ error: "not_found" }, 404);
  }

  async claim(code) {
    // A code is free if it was never used or its session is over.
    if (this.room && !this.room.ended) return json({ error: "taken" }, 409);
    await this.ctx.storage.deleteAll();
    this.room = {
      code,
      createdAt: Date.now(),
      hostKey: randomToken(16),
      hostId: null,
      ended: false,
      settings: { approval: "ask", guestsCanAdd: true, guestsCanControl: false, votesReorder: true },
      members: {},
      pending: {},
      banned: [],
      playback: null,
      queue: [],
      votes: {},
      skip: { trackId: null, voters: [] },
      pendingAdds: [],
      chat: [],
      hostGoneSince: null,
      emptySince: Date.now(),
    };
    await this.save();
    await this.scheduleAlarm();
    return json({ hostKey: this.room.hostKey });
  }

  preview() {
    const room = this.room;
    const host = room.members[room.hostId];
    const track = room.playback?.track;
    return {
      code: room.code,
      host: host?.name ?? null,
      listeners: Object.values(room.members).filter((m) => m.connected).length,
      approval: room.settings.approval,
      playing: track ? { title: track.title, artist: track.artist, thumbnail: track.thumbnail ?? null } : null,
    };
  }

  // -------------------------------------------------------------------------------------
  // Sockets
  // -------------------------------------------------------------------------------------

  async webSocketMessage(ws, data) {
    await this.load();
    if (!this.room || this.room.ended) {
      safeSend(ws, { t: "ended", reason: "over" });
      ws.close(4410, "ended");
      return;
    }
    let msg;
    try {
      msg = JSON.parse(typeof data === "string" ? data : new TextDecoder().decode(data));
    } catch {
      return this.error(ws, "bad_message", "Messages are JSON");
    }
    if (!msg || typeof msg.t !== "string") return this.error(ws, "bad_message", "Missing type");

    const attachment = ws.deserializeAttachment() ?? {};
    if (msg.t === "ping") {
      safeSend(ws, { t: "pong", c: num(msg.c), s: Date.now() });
      if (attachment.memberId) this.touch(attachment.memberId);
      return;
    }
    if (msg.t === "hello") return this.hello(ws, attachment, msg);

    const member = attachment.memberId ? this.room.members[attachment.memberId] : null;
    if (!member) return this.error(ws, "not_joined", "Say hello first");
    this.touch(member.id);

    const handler = this.handlers[msg.t];
    if (!handler) return this.error(ws, "unknown_type", `Unknown message ${msg.t}`);
    await handler.call(this, ws, member, msg);
  }

  async webSocketClose(ws) {
    await this.load();
    await this.dropSocket(ws);
  }

  async webSocketError(ws) {
    await this.load();
    await this.dropSocket(ws);
  }

  async dropSocket(ws) {
    const room = this.room;
    if (!room) return;
    const attachment = ws.deserializeAttachment() ?? {};
    if (attachment.pendingId && room.pending[attachment.pendingId]) {
      delete room.pending[attachment.pendingId];
      this.sendToHost({ t: "request.cancel", id: attachment.pendingId });
      await this.save();
      return;
    }
    const member = attachment.memberId ? room.members[attachment.memberId] : null;
    if (!member) return;
    // Another socket of the same member (a quick reconnect) keeps them present.
    const stillHere = this.socketsOf(member.id).some((other) => other !== ws);
    if (stillHere) return;
    member.connected = false;
    member.lastSeen = Date.now();
    if (member.id === room.hostId) room.hostGoneSince = Date.now();
    this.dropSkipVote(member.id);
    this.markEmptiness();
    await this.save();
    await this.scheduleAlarm();
    this.broadcastRoom();
  }

  // -------------------------------------------------------------------------------------
  // Joining
  // -------------------------------------------------------------------------------------

  async hello(ws, attachment, msg) {
    const room = this.room;
    if (attachment.memberId) return this.welcome(ws, room.members[attachment.memberId]);

    const name = cleanName(msg.name);
    const device = typeof msg.device === "string" ? msg.device.slice(0, 64) : "";
    const mode = MODES.includes(msg.mode) ? msg.mode : "phone";

    // Coming back: the token is the member's identity for the life of the session.
    if (typeof msg.token === "string") {
      const member = Object.values(room.members).find((m) => m.token === msg.token);
      if (member) {
        member.connected = true;
        member.lastSeen = Date.now();
        if (name) member.name = name;
        if (member.id === room.hostId) room.hostGoneSince = null;
        ws.serializeAttachment({ ...attachment, memberId: member.id });
        room.emptySince = null;
        await this.save();
        this.welcome(ws, member);
        this.broadcastRoom(ws);
        return;
      }
    }

    if (device && room.banned.includes(device)) {
      safeSend(ws, { t: "denied", reason: "blocked" });
      ws.close(4403, "blocked");
      return;
    }

    const isCreator = typeof msg.hostKey === "string" && msg.hostKey === room.hostKey;
    const connectedCount = Object.values(room.members).filter((m) => m.connected).length;
    if (!isCreator && connectedCount >= MAX_MEMBERS) {
      safeSend(ws, { t: "denied", reason: "full" });
      ws.close(4409, "full");
      return;
    }

    const hostPresent = room.hostId && room.members[room.hostId]?.connected;
    const needsApproval = !isCreator && room.settings.approval === "ask" && room.hostId;
    if (needsApproval) {
      const id = randomId("p");
      room.pending[id] = { id, name: name || "Guest", device, mode, at: Date.now() };
      ws.serializeAttachment({ ...attachment, pendingId: id });
      await this.save();
      safeSend(ws, { t: "waiting", host: room.members[room.hostId]?.name ?? null, hostPresent: !!hostPresent });
      this.sendToHost({ t: "request", id, name: room.pending[id].name });
      return;
    }

    const member = this.addMember(name || "Guest", device, mode);
    if (isCreator || !room.hostId) {
      room.hostId = member.id;
      room.hostGoneSince = null;
    }
    ws.serializeAttachment({ ...attachment, memberId: member.id });
    await this.save();
    this.welcome(ws, member);
    this.broadcastRoom(ws);
  }

  addMember(name, device, mode) {
    const room = this.room;
    const id = randomId("m");
    const member = { id, name, device, mode, token: randomToken(24), joinedAt: Date.now(), lastSeen: Date.now(), connected: true };
    room.members[id] = member;
    room.emptySince = null;
    return member;
  }

  welcome(ws, member) {
    safeSend(ws, {
      t: "welcome",
      protocol: PROTOCOL,
      you: { id: member.id, token: member.token },
      room: this.snapshot(member),
      s: Date.now(),
    });
  }

  // -------------------------------------------------------------------------------------
  // Messages from members
  // -------------------------------------------------------------------------------------

  get handlers() {
    return {
      playback: this.onPlayback,
      queue: this.onQueue,
      add: this.onAdd,
      remove: this.onRemove,
      vote: this.onVote,
      skip: this.onSkip,
      control: this.onControl,
      react: this.onReact,
      chat: this.onChat,
      mode: this.onMode,
      rename: this.onRename,
      approve: this.onApprove,
      deny: this.onDeny,
      kick: this.onKick,
      host: this.onHost,
      settings: this.onSettings,
      leave: this.onLeave,
      end: this.onEnd,
    };
  }

  /** The host reports what is playing and where, as a position at a moment of server time. */
  async onPlayback(ws, member, msg) {
    if (!this.isHost(member)) return this.error(ws, "not_host", "Only the host sets playback");
    const room = this.room;
    const track = msg.track == null ? null : cleanTrack(msg.track);
    if (msg.track != null && !track) return this.error(ws, "bad_track", "That song can't be shared");
    const previous = room.playback;
    const previousId = previous?.track?.id ?? null;
    room.playback = {
      track,
      playing: !!msg.playing && !!track,
      buffering: !!msg.buffering,
      positionMs: Math.max(0, num(msg.positionMs)),
      at: num(msg.at) || Date.now(),
      rate: clamp(num(msg.rate) || 1, 0.25, 4),
    };
    let queueChanged = false;
    if ((track?.id ?? null) !== previousId) {
      room.skip = { trackId: track?.id ?? null, voters: [] };
      if (track) delete room.votes[track.id];
      this.broadcast({ t: "skip", skip: this.skipState() });
      // A pick that's already playing has arrived, whether or not it passed through Up Next.
      const before = room.pendingAdds.length;
      if (track) room.pendingAdds = room.pendingAdds.filter((p) => p.track.id !== track.id);
      queueChanged = room.pendingAdds.length !== before;
    }
    // A heartbeat (same song, same state, position where the last report said it would be)
    // is relayed but not stored: the stored anchor still predicts the position, and skipping
    // the write keeps storage use to real changes.
    const next = room.playback;
    const heartbeat = previous && !queueChanged &&
      previousId === (next.track?.id ?? null) &&
      previous.playing === next.playing &&
      previous.buffering === next.buffering &&
      previous.rate === next.rate &&
      Math.abs(expectedAt(previous, next.at) - next.positionMs) < 1500;
    if (!heartbeat) await this.save();
    this.broadcast({ t: "playback", playback: room.playback }, ws);
    if (queueChanged) this.broadcast({ t: "queue", queue: this.mergedQueue() });
  }

  /** The host's upcoming songs, in order, with who added each. */
  async onQueue(ws, member, msg) {
    if (!this.isHost(member)) return this.error(ws, "not_host", "Only the host sets the queue");
    const room = this.room;
    const items = Array.isArray(msg.items) ? msg.items.slice(0, MAX_QUEUE) : [];
    const seen = new Map();
    room.queue = [];
    for (const raw of items) {
      const track = cleanTrack(raw?.track);
      if (!track) continue;
      const n = (seen.get(track.id) ?? 0) + 1;
      seen.set(track.id, n);
      room.queue.push({ uid: `${track.id}#${n}`, track, addedBy: cleanPerson(raw.addedBy) });
    }
    // An add the host has now put in its player is acknowledged by appearing here.
    const now = Date.now();
    room.pendingAdds = room.pendingAdds.filter((p) => now - p.at < PENDING_ADD_TTL_MS && !seen.has(p.track.id));
    await this.save();
    this.broadcast({ t: "queue", queue: this.mergedQueue() });
  }

  async onAdd(ws, member, msg) {
    const room = this.room;
    if (!this.isHost(member) && !room.settings.guestsCanAdd) {
      return this.error(ws, "not_allowed", "The host has turned off adding songs");
    }
    if (!this.allow(member.id, "add", 30, 60_000)) return this.error(ws, "slow_down", "Too many songs at once");
    const track = cleanTrack(msg.track);
    if (!track || track.local) return this.error(ws, "bad_track", "That song can't be shared");
    const add = { uid: randomId("a"), track, addedBy: { id: member.id, name: member.name }, next: !!msg.next, at: Date.now() };
    room.pendingAdds.push(add);
    await this.save();
    this.sendToHost({ t: "add", uid: add.uid, track, addedBy: add.addedBy, next: add.next });
    safeSend(ws, { t: "added", track: { id: track.id, title: track.title }, next: add.next });
    this.broadcast({ t: "queue", queue: this.mergedQueue() });
  }

  async onRemove(ws, member, msg) {
    const room = this.room;
    const uid = String(msg.uid ?? "");
    const item = this.mergedQueue().find((q) => q.uid === uid);
    if (!item) return;
    const mine = item.addedBy?.id === member.id;
    if (!this.isHost(member) && !mine) return this.error(ws, "not_allowed", "Only the host or whoever added it can remove a song");
    const pending = room.pendingAdds.findIndex((p) => p.uid === uid);
    if (pending >= 0) room.pendingAdds.splice(pending, 1);
    await this.save();
    this.sendToHost({ t: "remove", uid, trackId: item.track.id, index: this.mergedQueue().findIndex((q) => q.uid === uid) });
    this.broadcast({ t: "queue", queue: this.mergedQueue() });
  }

  async onVote(ws, member, msg) {
    const room = this.room;
    const trackId = String(msg.trackId ?? "");
    if (!TRACK_ID_RE.test(trackId)) return;
    if (!this.allow(member.id, "vote", 40, 60_000)) return;
    const voters = new Set(room.votes[trackId] ?? []);
    if (msg.on === false) voters.delete(member.id);
    else voters.add(member.id);
    if (voters.size) room.votes[trackId] = [...voters];
    else delete room.votes[trackId];
    await this.save();
    this.broadcast({ t: "votes", votes: room.votes });
  }

  async onSkip(ws, member, msg) {
    const room = this.room;
    const current = room.playback?.track?.id;
    if (!current) return;
    if (this.isHost(member)) {
      // The host doesn't vote; the host skips.
      this.sendToHost({ t: "control", action: "next", from: { id: member.id, name: member.name } });
      return;
    }
    if (room.skip.trackId !== current) room.skip = { trackId: current, voters: [] };
    const voters = new Set(room.skip.voters);
    if (msg.on === false) voters.delete(member.id);
    else voters.add(member.id);
    room.skip.voters = [...voters];
    const state = this.skipState();
    if (state.voters.length >= state.needed) {
      room.skip = { trackId: current, voters: [] };
      this.sendToHost({ t: "control", action: "next", from: null, reason: "vote" });
      this.broadcast({ t: "skip", skip: this.skipState(), passed: true });
    } else {
      this.broadcast({ t: "skip", skip: state });
    }
    await this.save();
  }

  async onControl(ws, member, msg) {
    const room = this.room;
    const actions = ["play", "pause", "seek", "next", "prev"];
    if (!actions.includes(msg.action)) return this.error(ws, "bad_control", "Unknown control");
    if (this.isHost(member)) return;
    if (!room.settings.guestsCanControl) return this.error(ws, "not_allowed", "Only the host controls playback");
    if (!this.allow(member.id, "control", 20, 10_000)) return;
    this.sendToHost({
      t: "control",
      action: msg.action,
      positionMs: msg.action === "seek" ? Math.max(0, num(msg.positionMs)) : undefined,
      from: { id: member.id, name: member.name },
    });
  }

  async onReact(ws, member, msg) {
    if (!REACTIONS.includes(msg.emoji)) return;
    if (!this.allow(member.id, "react", 8, 4_000)) return;
    this.broadcast({ t: "react", emoji: msg.emoji, from: { id: member.id, name: member.name }, at: Date.now() });
  }

  async onChat(ws, member, msg) {
    const text = typeof msg.text === "string" ? msg.text.trim().slice(0, 500) : "";
    if (!text) return;
    if (!this.allow(member.id, "chat", 12, 10_000)) return this.error(ws, "slow_down", "You're sending messages too fast");
    const room = this.room;
    const reply = msg.replyTo && typeof msg.replyTo === "object"
      ? { id: String(msg.replyTo.id ?? "").slice(0, 40), name: cleanName(msg.replyTo.name) ?? "", text: String(msg.replyTo.text ?? "").slice(0, 140) }
      : null;
    const message = { id: randomId("c"), from: { id: member.id, name: member.name }, text, at: Date.now(), replyTo: reply };
    room.chat.push(message);
    if (room.chat.length > MAX_CHAT) room.chat.splice(0, room.chat.length - MAX_CHAT);
    await this.save();
    this.broadcast({ t: "chat", message });
  }

  async onMode(ws, member, msg) {
    if (!MODES.includes(msg.mode)) return;
    member.mode = msg.mode;
    await this.save();
    this.broadcastRoom();
  }

  async onRename(ws, member, msg) {
    const name = cleanName(msg.name);
    if (!name) return;
    member.name = name;
    await this.save();
    this.broadcastRoom();
  }

  async onApprove(ws, member, msg) {
    if (!this.isHost(member)) return;
    const room = this.room;
    const request = room.pending[msg.id];
    if (!request) return;
    delete room.pending[msg.id];
    const sockets = this.ctx.getWebSockets().filter((s) => s.deserializeAttachment()?.pendingId === request.id);
    if (!sockets.length) {
      await this.save();
      this.broadcastRoom();
      return;
    }
    const joined = this.addMember(request.name, request.device, request.mode);
    for (const s of sockets) {
      s.serializeAttachment({ ...(s.deserializeAttachment() ?? {}), pendingId: null, memberId: joined.id });
      this.welcome(s, joined);
    }
    await this.save();
    this.broadcastRoom();
  }

  async onDeny(ws, member, msg) {
    if (!this.isHost(member)) return;
    const room = this.room;
    const request = room.pending[msg.id];
    if (!request) return;
    delete room.pending[msg.id];
    if (msg.block && request.device) room.banned.push(request.device);
    await this.save();
    for (const s of this.ctx.getWebSockets()) {
      if (s.deserializeAttachment()?.pendingId === request.id) {
        safeSend(s, { t: "denied", reason: msg.block ? "blocked" : "declined" });
        s.close(4403, "declined");
      }
    }
    this.broadcastRoom();
  }

  async onKick(ws, member, msg) {
    if (!this.isHost(member)) return;
    const room = this.room;
    const target = room.members[msg.id];
    if (!target || target.id === member.id) return;
    delete room.members[target.id];
    if (msg.block && target.device) room.banned.push(target.device);
    this.dropSkipVote(target.id);
    await this.save();
    for (const s of this.socketsOf(target.id)) {
      safeSend(s, { t: "removed", blocked: !!msg.block });
      s.close(4403, "removed");
    }
    this.broadcastRoom();
  }

  async onHost(ws, member, msg) {
    if (!this.isHost(member)) return;
    const target = this.room.members[msg.id];
    if (!target || !target.connected) return;
    this.room.hostId = target.id;
    this.room.hostGoneSince = null;
    await this.save();
    this.broadcastRoom();
  }

  async onSettings(ws, member, msg) {
    if (!this.isHost(member)) return;
    const settings = this.room.settings;
    if (APPROVALS.includes(msg.approval)) settings.approval = msg.approval;
    for (const key of ["guestsCanAdd", "guestsCanControl", "votesReorder"]) {
      if (typeof msg[key] === "boolean") settings[key] = msg[key];
    }
    // Opening the door lets in everyone who was waiting at it.
    if (settings.approval === "open") {
      for (const id of Object.keys(this.room.pending)) await this.onApprove(ws, member, { id });
    }
    await this.save();
    this.broadcastRoom();
  }

  async onLeave(ws, member) {
    const room = this.room;
    delete room.members[member.id];
    this.dropSkipVote(member.id);
    if (room.hostId === member.id) {
      room.hostId = null;
      this.promoteHost();
    }
    this.markEmptiness();
    ws.serializeAttachment({ memberId: null, pendingId: null });
    await this.save();
    await this.scheduleAlarm();
    safeSend(ws, { t: "left" });
    ws.close(1000, "left");
    this.broadcastRoom();
  }

  async onEnd(ws, member) {
    if (!this.isHost(member)) return;
    this.room.ended = true;
    this.room.playback = null;
    await this.save();
    this.broadcast({ t: "ended", reason: "host" });
    for (const s of this.ctx.getWebSockets()) s.close(1000, "ended");
    await this.ctx.storage.setAlarm(Date.now() + 60_000);
  }

  // -------------------------------------------------------------------------------------
  // Time-based upkeep
  // -------------------------------------------------------------------------------------

  async alarm() {
    await this.load();
    const room = this.room;
    if (!room) return;
    const now = Date.now();
    if (room.ended || (room.emptySince && now - room.emptySince >= EMPTY_ROOM_TTL_MS)) {
      await this.ctx.storage.deleteAll();
      this.room = null;
      return;
    }
    let changed = false;
    for (const member of Object.values(room.members)) {
      if (!member.connected && now - member.lastSeen >= MEMBER_FORGET_MS && member.id !== room.hostId) {
        delete room.members[member.id];
        changed = true;
      }
    }
    if (room.hostGoneSince && now - room.hostGoneSince >= HOST_GRACE_MS) {
      if (this.promoteHost()) changed = true;
    }
    if (changed) {
      await this.save();
      this.broadcastRoom();
    }
    await this.scheduleAlarm();
  }

  async scheduleAlarm() {
    const room = this.room;
    if (!room) return;
    const times = [];
    if (room.ended) times.push(Date.now() + 60_000);
    if (room.emptySince) times.push(room.emptySince + EMPTY_ROOM_TTL_MS);
    if (room.hostGoneSince) times.push(room.hostGoneSince + HOST_GRACE_MS);
    for (const m of Object.values(room.members)) if (!m.connected) times.push(m.lastSeen + MEMBER_FORGET_MS);
    if (!times.length) return;
    await this.ctx.storage.setAlarm(Math.max(Date.now() + 1_000, Math.min(...times)));
  }

  /** Hands the room to whoever has been here longest, so the music outlives the host leaving. */
  promoteHost() {
    const room = this.room;
    const next = Object.values(room.members)
      .filter((m) => m.connected && m.id !== room.hostId)
      .sort((a, b) => a.joinedAt - b.joinedAt)[0];
    if (!next) return false;
    const previous = room.members[room.hostId];
    room.hostId = next.id;
    room.hostGoneSince = null;
    this.broadcast({ t: "notice", kind: "host_changed", name: next.name, previous: previous?.name ?? null });
    return true;
  }

  markEmptiness() {
    const room = this.room;
    const anyone = Object.values(room.members).some((m) => m.connected);
    room.emptySince = anyone ? null : room.emptySince ?? Date.now();
  }

  // -------------------------------------------------------------------------------------
  // State views and delivery
  // -------------------------------------------------------------------------------------

  snapshot(forMember) {
    const room = this.room;
    const isHost = forMember?.id === room.hostId;
    return {
      code: room.code,
      hostId: room.hostId,
      settings: room.settings,
      members: Object.values(room.members)
        .sort((a, b) => a.joinedAt - b.joinedAt)
        .map((m) => ({ id: m.id, name: m.name, mode: m.mode, connected: m.connected, joinedAt: m.joinedAt })),
      playback: room.playback,
      queue: this.mergedQueue(),
      votes: room.votes,
      skip: this.skipState(),
      chat: room.chat,
      requests: isHost ? Object.values(room.pending).map((p) => ({ id: p.id, name: p.name })) : [],
    };
  }

  /** The host's queue with guests' adds already in place while the host catches up. */
  mergedQueue() {
    const room = this.room;
    const now = Date.now();
    const pending = room.pendingAdds.filter((p) => now - p.at < PENDING_ADD_TTL_MS);
    const next = pending.filter((p) => p.next).reverse();
    const later = pending.filter((p) => !p.next);
    const view = (p) => ({ uid: p.uid, track: p.track, addedBy: p.addedBy, pending: true });
    return [...next.map(view), ...room.queue, ...later.map(view)];
  }

  skipState() {
    const room = this.room;
    const connected = Object.values(room.members).filter((m) => m.connected).length;
    return {
      trackId: room.skip.trackId,
      voters: room.skip.voters,
      needed: Math.max(2, Math.ceil(connected / 2)),
    };
  }

  dropSkipVote(memberId) {
    const skip = this.room.skip;
    skip.voters = skip.voters.filter((id) => id !== memberId);
  }

  broadcastRoom(except) {
    const room = this.room;
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue;
      const memberId = ws.deserializeAttachment()?.memberId;
      const member = memberId ? room.members[memberId] : null;
      if (!member) continue;
      safeSend(ws, {
        t: "room",
        hostId: room.hostId,
        settings: room.settings,
        members: this.snapshot(member).members,
        requests: member.id === room.hostId ? Object.values(room.pending).map((p) => ({ id: p.id, name: p.name })) : [],
        skip: this.skipState(),
      });
    }
  }

  broadcast(message, except) {
    const text = JSON.stringify(message);
    for (const ws of this.ctx.getWebSockets()) {
      if (ws === except) continue;
      if (!ws.deserializeAttachment()?.memberId) continue;
      try {
        ws.send(text);
      } catch {}
    }
  }

  sendToHost(message) {
    const hostId = this.room.hostId;
    if (!hostId) return;
    for (const ws of this.socketsOf(hostId)) safeSend(ws, message);
  }

  socketsOf(memberId) {
    return this.ctx.getWebSockets().filter((ws) => ws.deserializeAttachment()?.memberId === memberId);
  }

  isHost(member) {
    return member.id === this.room.hostId;
  }

  touch(memberId) {
    const member = this.room.members[memberId];
    if (member) member.lastSeen = Date.now();
  }

  allow(memberId, kind, limit, windowMs) {
    const key = `${memberId}:${kind}`;
    const now = Date.now();
    const bucket = (this.buckets.get(key) ?? []).filter((t) => now - t < windowMs);
    if (bucket.length >= limit) {
      this.buckets.set(key, bucket);
      return false;
    }
    bucket.push(now);
    this.buckets.set(key, bucket);
    return true;
  }

  error(ws, code, message) {
    safeSend(ws, { t: "error", code, message });
  }
}

// ---------------------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------------------

function cleanTrack(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id ?? "");
  if (!TRACK_ID_RE.test(id)) return null;
  const title = String(raw.title ?? "").trim().slice(0, 200);
  if (!title) return null;
  const track = {
    id,
    title,
    artist: String(raw.artist ?? "").trim().slice(0, 200),
    durationMs: Math.max(0, Math.min(num(raw.durationMs), 24 * 3600_000)),
  };
  if (raw.album) track.album = String(raw.album).trim().slice(0, 200);
  if (typeof raw.thumbnail === "string" && IMAGE_RE.test(raw.thumbnail)) track.thumbnail = raw.thumbnail;
  if (raw.artistId) track.artistId = String(raw.artistId).slice(0, 64);
  if (raw.explicit) track.explicit = true;
  if (raw.local) track.local = true;
  return track;
}

function cleanPerson(raw) {
  if (!raw || typeof raw !== "object") return null;
  const name = cleanName(raw.name);
  if (!name) return null;
  return { id: String(raw.id ?? "").slice(0, 40) || null, name };
}

function cleanName(raw) {
  if (typeof raw !== "string") return null;
  const name = raw.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 24);
  return name || null;
}

/** Where [playback] puts the song at server time [at]. */
function expectedAt(playback, at) {
  if (!playback.playing || playback.buffering) return playback.positionMs;
  return playback.positionMs + Math.max(0, at - playback.at) * (playback.rate || 1);
}

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function safeSend(ws, message) {
  try {
    ws.send(JSON.stringify(message));
  } catch {}
}

function randomCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
  return [...bytes].map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

function randomToken(bytes) {
  return [...crypto.getRandomValues(new Uint8Array(bytes))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function randomId(prefix) {
  return `${prefix}_${randomToken(5)}`;
}

// ---------------------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------------------

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

class HttpError extends Error {
  constructor(status, code, message) {
    super(message ?? code);
    this.status = status;
    this.code = code;
  }
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...CORS },
  });
}

function html(body, status = 200) {
  return new Response(body, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-cache" } });
}
