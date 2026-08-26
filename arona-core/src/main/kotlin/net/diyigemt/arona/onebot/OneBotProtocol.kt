package net.diyigemt.arona.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import java.util.UUID

object OneBotProtocol {
  fun parsePayload(payload: String): ParsedPayload? {
    val json = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return null
    return if (json.has("post_type")) ParsedPayload.Event(parseEvent(json))
    else if (json.has("action") && json.has("echo")) ParsedPayload.Action(parseAction(json))
    else if (json.has("echo")) ParsedPayload.Response(parseResponse(json))
    else null
  }

  fun action(name: String, params: JsonObject = JsonObject()): OneBotAction =
    OneBotAction(name, params, UUID.randomUUID().toString())

  fun serialize(action: OneBotAction): String = JsonObject().apply {
    addProperty("action", action.action)
    add("params", action.params)
    addProperty("echo", action.echo)
  }.toString()

  fun messageToJson(message: OutgoingMessage): JsonArray = JsonArray().apply {
    message.segments.forEach { segment ->
      add(JsonObject().apply {
        when (segment) {
          is MessageSegment.Text -> {
            addProperty("type", "text")
            add("data", JsonObject().apply { addProperty("text", segment.value) })
          }
          is MessageSegment.Image -> {
            addProperty("type", "image")
            add("data", JsonObject().apply {
              val value = segment.url ?: segment.file ?: segment.data?.let {
                "base64://${java.util.Base64.getEncoder().encodeToString(it)}"
              } ?: ""
              addProperty("file", value)
            })
          }
        }
      })
    }
  }

  fun extractText(event: OneBotEvent): String {
    event.rawMessage?.let { return it }
    val message = event.message ?: return ""
    if (message.isJsonPrimitive) return message.asString
    if (!message.isJsonArray) return ""
    return message.asJsonArray.joinToString("") { segment ->
      val objectValue = segment.asJsonObject
      if (objectValue.get("type")?.asString == "text") {
        objectValue.getAsJsonObject("data")?.get("text")?.asString.orEmpty()
      } else ""
    }
  }

  private fun parseEvent(json: JsonObject) = OneBotEvent(
    time = json.long("time") ?: System.currentTimeMillis() / 1000,
    selfId = json.long("self_id") ?: 0,
    postType = json.string("post_type").orEmpty(),
    messageType = json.string("message_type"),
    subType = json.string("sub_type"),
    messageId = json.long("message_id"),
    userId = json.long("user_id"),
    groupId = json.long("group_id"),
    rawMessage = json.string("raw_message"),
    message = json.get("message"),
    sender = json.getAsJsonObject("sender"),
    raw = json,
  )

  private fun parseAction(json: JsonObject) = OneBotAction(
    action = json.get("action")?.asString.orEmpty(),
    params = json.getAsJsonObject("params") ?: JsonObject(),
    echo = json.get("echo")?.asString.orEmpty(),
  )

  private fun parseResponse(json: JsonObject) = OneBotActionResponse(
    status = json.string("status").orEmpty(),
    retcode = json.get("retcode")?.asInt ?: -1,
    data = json.get("data"),
    message = json.string("message"),
    wording = json.string("wording"),
    echo = json.string("echo"),
    raw = json,
  )

  private fun JsonObject.string(name: String): String? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asString
  private fun JsonObject.long(name: String): Long? = get(name)?.takeUnless(JsonElement::isJsonNull)?.asLong
}

sealed interface ParsedPayload {
  data class Event(val value: OneBotEvent) : ParsedPayload
  data class Action(val value: OneBotAction) : ParsedPayload
  data class Response(val value: OneBotActionResponse) : ParsedPayload
}

