package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.runtime.CommandContext
import net.diyigemt.arona.runtime.ForwardMessage
import net.diyigemt.arona.runtime.MessageSegment
import net.diyigemt.arona.runtime.OutgoingMessage
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.standalone.StandaloneAronaConfig

/**
 * 独立模式的 /config 指令:
 * /config             查看全部配置(合并转发)
 * /config <配置名>     查看单个配置
 * /config <配置名> <值> 修改配置并热重载
 */
object StandaloneConfigCommand {

  private const val USAGE =
    "用法: /config 查看全部配置; /config <配置名> 查看单个; /config <配置名> <值> 修改配置; /config groups|managers|notify.black_groups add|del [数值] 追加/删除(群内省略数值时用当前群号)"

  fun handle(context: CommandContext, arguments: List<String>): OutgoingMessage = when {
    arguments.isEmpty() -> viewAll()
    arguments.size == 1 -> viewOne(arguments[0])
    isListMutation(arguments) -> mutateList(context, arguments)
    else -> update(context, arguments[0], arguments.drop(1).joinToString(" "))
  }

  /** /config <列表名> add|del [数值]：向列表配置追加/移除一个值，群内省略数值时使用当前群号 */
  private fun mutateList(context: CommandContext, arguments: List<String>): OutgoingMessage {
    val isAdd = arguments[1].equals("add", ignoreCase = true)
    val value = when {
      arguments.size >= 3 -> arguments[2].toLongOrNull()
        ?: return OutgoingMessage.text("数值格式错误: ${arguments[2]}")
      context.groupId != null -> context.groupId
      else -> return OutgoingMessage.text("请在群里发送 /config ${arguments[0]} ${arguments[1]}，或指定要${if (isAdd) "添加" else "删除"}的数值")
    }
    return OutgoingMessage.text(
      if (isAdd) StandaloneAronaConfig.addToList(arguments[0], value, showValue = context.groupId == null)
      else StandaloneAronaConfig.removeFromList(arguments[0], value, showValue = context.groupId == null)
    )
  }

  private fun isListMutation(arguments: List<String>): Boolean {
    if (!arguments[1].equals("add", ignoreCase = true) && !arguments[1].equals("del", ignoreCase = true)) return false
    val field = StandaloneAronaConfig.findField(arguments[0]) ?: return false
    return field.get(StandaloneAronaConfig.config) is List<*>
  }

  private fun viewAll(): OutgoingMessage {
    val config = StandaloneAronaConfig.config
    val nodes = mutableListOf<ForwardMessage>().apply {
      add(headerNode())
      StandaloneAronaConfig.fields.forEach { field ->
        add(
          ForwardMessage(
            name = "Arona",
            uin = RuntimeConfig.botId,
            content = listOf(MessageSegment.Text(fieldLine(field.key, field.get(config), field.description))),
          )
        )
      }
    }
    return OutgoingMessage.forward("Arona 全部配置", nodes)
  }

  private fun viewOne(key: String): OutgoingMessage {
    val field = StandaloneAronaConfig.findField(key)
      ?: return OutgoingMessage.text("未找到配置项: $key\n$USAGE")
    return OutgoingMessage.text(fieldLine(field.key, field.get(StandaloneAronaConfig.config), field.description))
  }

  private fun update(context: CommandContext, key: String, value: String): OutgoingMessage =
    OutgoingMessage.text(StandaloneAronaConfig.update(key, value, showValue = context.groupId == null))

  private fun headerNode(): ForwardMessage = ForwardMessage(
    name = "Arona",
    uin = RuntimeConfig.botId,
    content = listOf(
      MessageSegment.Text(
        "Arona 全部配置\n配置文件: arona-standalone/arona.yml (修改保存后自动热重载)\n$USAGE"
      )
    ),
  )

  private fun fieldLine(key: String, value: Any?, description: String): String = buildString {
    appendLine("【$key】$value")
    appendLine("说明: $description")
  }
}
