package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture

class OneBotHttpForwardConnection(
  config: EndpointConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private val client = OkHttpClient()

  override fun start() {
    require(config.url.isNotBlank()) { "HTTP forward url must not be blank" }
    println("[OneBot HTTP] forward started: ${config.url}")
    scheduleHeartbeat()
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    val request = Request.Builder().url(config.url).applyToken().post(
      OneBotProtocol.serialize(action).toRequestBody("application/json; charset=utf-8".toMediaType())
    ).build()
    pending[action.echo] = result
    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) { pending.remove(action.echo); result.completeExceptionally(e) }
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val body = it.body?.string().orEmpty()
          println("[OneBot HTTP response] $body")
          val parsed = OneBotProtocol.parsePayload(body)
          val value = (parsed as? ParsedPayload.Response)?.value
          pending.remove(action.echo)
          result.complete(value)
        }
      }
    })
    return result
  }

  override fun sendRaw(payload: String) = Unit

  private fun Request.Builder.applyToken(): Request.Builder = apply {
    if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}")
  }
}

class OneBotHttpReverseConnection(
  config: EndpointConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private val client = OkHttpClient()

  override fun start() {
    require(config.url.isNotBlank()) { "HTTP reverse url must not be blank" }
    println("[OneBot HTTP] reverse started: ${config.url}")
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    val request = Request.Builder().url(config.url).applyToken().post(
      OneBotProtocol.serialize(action).toRequestBody("application/json; charset=utf-8".toMediaType())
    ).build()
    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) { result.completeExceptionally(e) }
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val body = it.body?.string().orEmpty()
          println("[OneBot HTTP reverse response] $body")
          val parsed = OneBotProtocol.parsePayload(body)
          result.complete((parsed as? ParsedPayload.Response)?.value)
        }
      }
    })
    return result
  }

  override fun sendRaw(payload: String) {
    client.newCall(Request.Builder().url(config.url).applyToken().post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build())
      .enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { println("[OneBot HTTP reverse] ${e.message}") }
        override fun onResponse(call: Call, response: Response) { response.close() }
      })
  }

  private fun Request.Builder.applyToken(): Request.Builder = apply {
    if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}")
  }
}

