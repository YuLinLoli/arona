package net.diyigemt.arona.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import java.io.File
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
          is MessageSegment.At -> {
            addProperty("type", "at")
            add("data", JsonObject().apply { addProperty("qq", segment.userId) })
          }
          is MessageSegment.Forward -> {
            // send_group_msg/send_private_msg 不支持 forward 段, 合并转发走 send_forward_msg 专用通道
            addProperty("type", "text")
            add("data", JsonObject().apply { addProperty("text", "[合并转发:${segment.title}]") })
          }
          is MessageSegment.Image -> {
            addProperty("type", "image")
            add("data", JsonObject().apply {
              val value = segment.url ?: segment.data?.let {
                "base64://${java.util.Base64.getEncoder().encodeToString(it)}"
              } ?: segment.file?.let { filePath ->
                val file = File(filePath)
                if (file.isFile) {
                  "base64://${java.util.Base64.getEncoder().encodeToString(file.readBytes())}"
                } else {
                  filePath
                }
              } ?: ""
              addProperty("file", value)
            })
          }
        }
      })
    }
  }

  fun extractText(event: OneBotEvent): String = extractSegments(event).joinToString("") { segment ->
    when (segment) {
      is MessageSegment.Text -> segment.value
      is MessageSegment.At -> "@${segment.userId}"
      is MessageSegment.Forward -> "[合并转发:${segment.title}]"
      is MessageSegment.Image -> formatImage(segment)
    }
  }

  /** 黑窗口显示的表情占位符： [表情 [id]]，无 id 时显示 [表情] */
  fun formatFace(id: String?): String = if (id.isNullOrBlank()) "[表情]" else "[表情 [$id]]"

  /** 黑窗口显示的图片占位符，格式仿 CQ 码： [arona:image,url=...] / [arona:image,file=...] */
  fun formatImage(image: MessageSegment.Image): String = when {
    image.url != null -> "[arona:image,url=${image.url}]"
    image.file != null -> "[arona:image,file=${image.file}]"
    else -> "[arona:image]"
  }

  fun extractSegments(event: OneBotEvent): List<MessageSegment> {
    val message = event.message
    if (message != null && message.isJsonArray) {
      return message.asJsonArray.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val segment = element.asJsonObject
        val type = segment.get("type")?.asString ?: return@mapNotNull null
        val data = segment.getAsJsonObject("data") ?: JsonObject()
        when (type) {
          "text" -> MessageSegment.Text(data.get("text")?.asString.orEmpty())
          "at" -> parseAtSegment(data.get("qq"))
          "image" -> MessageSegment.Image(
            url = data.get("url")?.asString,
            file = data.get("file")?.asString,
          )
          "face" -> MessageSegment.Text(formatFace(data.get("id")?.asString))
          else -> null
        }
      }
    }
    return decodeCqMessage(event.rawMessage.orEmpty())
  }

  private fun decodeCqMessage(raw: String): List<MessageSegment> {
    val result = mutableListOf<MessageSegment>()
    val pattern = Regex("""\[CQ:([a-z]+)((?:,[a-zA-Z0-9_-]+=[^,\]]*)*)\]""")
    val keyValue = Regex("""([a-zA-Z0-9_-]+)=([^,\]]*)""")
    var index = 0
    pattern.findAll(raw).forEach { match ->
      if (match.range.first > index) {
        result.add(MessageSegment.Text(raw.substring(index, match.range.first)))
      }
      val type = match.groupValues[1]
      val params = keyValue.findAll(match.groupValues[2])
        .associate { it.groupValues[1] to it.groupValues[2] }
      when (type) {
        "at" -> when (val qq = params["qq"]) {
          null -> Unit
          "all" -> result.add(MessageSegment.Text("@全体成员"))
          else -> qq.toLongOrNull()?.let { result.add(MessageSegment.At(it)) }
        }
        "image" -> result.add(MessageSegment.Image(file = params["file"], url = params["url"]))
        "face" -> result.add(MessageSegment.Text(formatFace(params["id"])))
        else -> result.add(MessageSegment.Text(match.value))
      }
      index = match.range.last + 1
    }
    if (index < raw.length) result.add(MessageSegment.Text(raw.substring(index)))
    return result
  }

  /** 解析 at 段: qq 可能是 "all"(@全体成员) 或数字, 非法值直接忽略 */
  private fun parseAtSegment(qq: JsonElement?): MessageSegment? {
    val value = qq?.takeUnless(JsonElement::isJsonNull)?.asString ?: return null
    return when {
      value == "all" -> MessageSegment.Text("@全体成员")
      else -> value.toLongOrNull()?.let(MessageSegment::At)
    }
  }

  private fun parseEvent(json: JsonObject) = OneBotEvent(
    time = json.long("time") ?: System.currentTimeMillis() / 1000,
    selfId = json.long("self_id") ?: 0,
    postType = json.string("post_type").orEmpty(),
    noticeType = json.string("notice_type"),
    messageType = json.string("message_type"),
    subType = json.string("sub_type"),
    messageId = json.long("message_id"),
    userId = json.long("user_id"),
    operatorId = json.long("operator_id"),
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

