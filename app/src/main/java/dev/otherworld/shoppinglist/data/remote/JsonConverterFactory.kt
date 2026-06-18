package dev.otherworld.shoppinglist.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * A small Retrofit converter backed by kotlinx.serialization. Avoids depending on the
 * third-party kotlinx converter (whose public API is awkward to call from Kotlin) and keeps
 * full control over generic envelope types like `OcsResponse<List<ItemDto>>`.
 */
class JsonConverterFactory(private val json: Json) : Converter.Factory() {

    private val contentType = "application/json".toMediaType()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            body.use { json.decodeFromString(serializer, it.string()) }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<Any?, RequestBody> { value ->
            json.encodeToString(serializer, value).toRequestBody(contentType)
        }
    }
}
