package com.shiny.music.discord

import org.json.JSONObject

data class GatewayReadyEvent(
    val user: GatewayReadyUser?,
    val sessionId: String?,
) {
    companion object {
        // OAuth2 sessions can get partial objects, so nothing here is required.
        fun fromJson(obj: JSONObject): GatewayReadyEvent =
            GatewayReadyEvent(
                user = obj.optJSONObject("user")?.let { user ->
                    GatewayReadyUser(
                        id = user.optString("id"),
                        username = user.optString("username").ifBlank { null },
                    )
                },
                sessionId = obj.optString("session_id").ifBlank { null },
            )
    }
}

data class GatewayReadyUser(
    val id: String,
    val username: String?,
)

/** Why a Gateway session ended: Discord's close code, or one of the negative local codes in GatewayUtils. */
data class GatewayCloseInfo(
    val code: Int,
    val reason: String,
) {
    /** Reconnecting with the same token can't help. */
    val fatal: Boolean get() = code in FATAL_CLOSE_CODES
}

/** Discord closed the session with 4004: it doesn't accept the token for the Gateway. */
class GatewayAuthenticationException(
    val close: GatewayCloseInfo,
) : Exception("Discord Gateway rejected the token (${close.code} ${close.reason})")

class GatewayClosedException(
    val close: GatewayCloseInfo,
) : Exception("Discord Gateway closed (${close.code} ${close.reason})")
