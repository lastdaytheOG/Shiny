package com.shiny.music.discord

internal const val GATEWAY_URL = "wss://gateway.discord.gg/?v=9&encoding=json"

internal object GatewayOp {
    const val DISPATCH = 0
    const val HEARTBEAT = 1
    const val IDENTIFY = 2
    const val PRESENCE_UPDATE = 3
    const val RECONNECT = 7
    const val INVALID_SESSION = 9
    const val HELLO = 10
    const val HEARTBEAT_ACK = 11
}

/** Discord's close code for a token it won't accept. */
internal const val CLOSE_AUTHENTICATION_FAILED = 4004

/** Closes a reconnect can't fix: authentication, sharding, API version, intents. */
internal val FATAL_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)

// Failures the client notices itself. Negative, so they never collide with Discord's codes.
internal const val CLOSE_NETWORK_FAILURE = -1
internal const val CLOSE_TIMEOUT = -2
internal const val CLOSE_ZOMBIE = -3
internal const val CLOSE_RECONNECT_REQUESTED = -4
internal const val CLOSE_INVALID_SESSION = -5
