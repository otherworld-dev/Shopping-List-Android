package dev.otherworld.shoppinglist.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Standard OCS v2 response envelope: { "ocs": { "meta": {...}, "data": <T> } }. */
@Serializable
data class OcsResponse<T>(
    val ocs: OcsBody<T>,
)

@Serializable
data class OcsBody<T>(
    val meta: OcsMeta,
    val data: T,
)

@Serializable
data class OcsMeta(
    val status: String = "",
    @SerialName("statuscode") val statusCode: Int = 0,
    val message: String? = null,
)
