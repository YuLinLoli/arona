package net.diyigemt.arona.onebot

import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.MessageTarget
import net.diyigemt.arona.runtime.OutgoingMessage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 独立运行模式的控制台输出：黄色启动横幅、仿 Mirai Console 的消息行。
 */
object OneBotConsole {
  const val ANSI_YELLOW = "\u001b[33m"
  const val ANSI_RESET = "\u001b[0m"

  private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  private val BANNER = listOf(
    " █████╗ ██████╗ ██████╗ ███╗   ██╗ █████╗ ",
    " ██╔══██╗██╔══██╗██╔═══██╗████╗  ██║██╔══██╗",
    " ███████║██████╔╝██║   ██║██╔██╗ ██║███████║",
    " ██╔══██║██╔══██╗██║   ██║██║╚██╗██║██╔══██║",
    " ██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║  ██║",
    " ╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝  ╚═╝",
  )

  fun printBanner(version: String) {
    val lines = BANNER.toMutableList()
    lines[lines.lastIndex] += "     v$version"
    println(ANSI_YELLOW + lines.joinToString("\n") + ANSI_RESET)
  }

  /** 打印收到的消息，格式与 Mirai Console 一致：V/Bot.<botId>: [群名(群号)] 群名片(QQ) -> 内容 */
  fun printMessage(selfId: Long, event: OneBotEvent, groupName: String?) {
    println(formatMessage(selfId, event, groupName))
  }

  /** 打印自身触发的消息（黄色），格式：<时间> V/Bot.<botId>: Group(群号)/Friend(QQ) <- 内容 */
  fun printOutgoing(selfId: Long, target: MessageTarget, message: OutgoingMessage) {
    val address = when (target) {
      is MessageTarget.Group -> "Group(${target.id})"
      is MessageTarget.Private -> "Friend(${target.id})"
    }
    val line = "${LocalDateTime.now().format(TIME_FORMAT)} V/Bot.$selfId: $address <- ${formatOutgoing(message)}"
    println(ANSI_YELLOW + line + ANSI_RESET)
  }

  fun formatMessage(selfId: Long, event: OneBotEvent, groupName: String?): String {
    val senderName = event.sender?.get("card")?.asString?.takeIf(String::isNotBlank)
      ?: event.sender?.get("nickname")?.asString
      ?: event.userId?.toString()
      ?: "未知"
    val sender = "$senderName(${event.userId ?: "?"})"
    val text = OneBotProtocol.extractText(event)
    // 黑窗口可能无法显示彩色 emoji，人名/群名/聊天内容统一降级为 [emoji:编号]
    return ConsoleEmoji.sanitize(
      if (event.messageType == "group" && event.groupId != null) {
        val group = if (groupName.isNullOrBlank()) event.groupId.toString() else "$groupName(${event.groupId})"
        "V/Bot.$selfId: [$group] $sender -> $text"
      } else {
        "V/Bot.$selfId: $sender -> $text"
      }
    )
  }

  internal fun formatOutgoing(message: OutgoingMessage): String = ConsoleEmoji.sanitize(
    message.segments.joinToString("") { segment ->
      when (segment) {
        is MessageSegment.Text -> segment.value
        is MessageSegment.At -> "[at:qq=${segment.userId}]"
        is MessageSegment.Forward -> "[合并转发:${segment.title}]"
        is MessageSegment.Image -> when {
          segment.data != null -> {
            val base64 = java.util.Base64.getEncoder().encodeToString(segment.data)
            "[arona:image,url=base64://${truncate(base64)}]"
          }
          segment.url != null -> "[arona:image,url=${segment.url}]"
          segment.file != null -> "[arona:image,file=${segment.file}]"
          else -> "[arona:image]"
        }
      }
    }
  )

  private fun truncate(value: String, max: Int = 40): String =
    if (value.length <= max) value else value.take(max) + "..."
}
