package dev.otherworld.shoppinglist.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Minimal slice of /cloud/capabilities needed to discover the notify_push WebSocket endpoint. */
@Serializable
data class CapabilitiesResponse(
    val capabilities: CapabilitiesBlock = CapabilitiesBlock(),
)

@Serializable
data class CapabilitiesBlock(
    @SerialName("notify_push") val notifyPush: NotifyPushCaps? = null,
    val theming: ThemingCaps? = null,
)

@Serializable
data class ThemingCaps(
    /** The instance's primary brand colour, e.g. "#3B8338". */
    val color: String? = null,
    @SerialName("color-element") val colorElement: String? = null,
    @SerialName("color-element-bright") val colorElementBright: String? = null,
    val name: String? = null,
)

@Serializable
data class NotifyPushCaps(
    val endpoints: NotifyPushEndpoints? = null,
)

@Serializable
data class NotifyPushEndpoints(
    val websocket: String? = null,
)
