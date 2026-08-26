package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture

class OneBotHttpReverseConnection(
  config: OneBotConnectionConfig,
  handler: OneBotEventHandler,
) : AbstractOneBotConnection(config, handler) {
  private val client = OkHttpClient()

  override fun start() {
    require(config.endpoint.url.isNotBlank()) { "HTTP reverse url must not be blank" }
    println("[OneBot reverse HTTP] 目标: ${config.endpoint.url}")
    scheduleHeartbeat()
  }

  override fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?> {
    val result = CompletableFuture<OneBotActionResponse?>()
    post(OneBotProtocol.serialize(action), result)
    return result
  }

  override fun sendRaw(payload: String) { post(payload, null) }

  private fun post(payload: String, future: CompletableFuture<OneBotActionResponse?>?) {
    val request = Request.Builder().url(config.endpoint.url).applyToken().post(
      payload.toRequestBody("application/json; charset=utf-8".toMediaType())
    ).build()
    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, error: IOException) {
        println("[OneBot reverse HTTP] ${error.message}")
        future?.completeExceptionally(error)
      }
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val body = it.body?.string().orEmpty()
          future?.complete((OneBotProtocol.parsePayload(body) as? ParsedPayload.Response)?.value)
        }
      }
    })
  }

  private fun Request.Builder.applyToken() = apply {
    if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}")
  }
}
