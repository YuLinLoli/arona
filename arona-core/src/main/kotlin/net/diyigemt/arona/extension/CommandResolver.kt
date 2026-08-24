/**
 * 文件说明：本文件属于 命令拦截与扩展机制。
 * 具体职责：围绕 CommandResolver 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.extension

import net.diyigemt.arona.config.AronaConfig
import net.diyigemt.arona.service.AronaService
import net.diyigemt.arona.service.AronaServiceManager
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.parse.CommandCall
import net.mamoe.mirai.console.command.resolve.CommandCallInterceptor
import net.mamoe.mirai.console.command.resolve.InterceptResult
import net.mamoe.mirai.console.command.resolve.InterceptedReason
import net.mamoe.mirai.console.extensions.CommandCallInterceptorProvider
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.Message

@OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
// 中文说明：定义 CommandResolver 对象，集中提供本文件的共享功能。
object CommandResolver: CommandCallInterceptorProvider {
  override val instance: CommandCallInterceptor
    get() = CommandResolverInterceptor
}

@OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
// 中文说明：定义 CommandResolverInterceptor 对象，集中提供本文件的共享功能。
private object CommandResolverInterceptor: CommandCallInterceptor {
  override fun interceptCall(call: CommandCall): InterceptResult<CommandCall>? {
    CommandInterceptorManager.emitInterceptCall(call)
    return super.interceptCall(call)
  }

  override fun interceptBeforeCall(message: Message, caller: CommandSender): InterceptResult<Message>? {
    val unreliableCommandName = extraCommandName(message)
    val matchCommand = CommandManager.matchCommand(unreliableCommandName)
    if (matchCommand is AronaService && caller is UserCommandSender) {
      if (caller.bot.id != AronaConfig.qq) {
        return InterceptResult(InterceptedReason("非本插件消息"))
      }
      val s = AronaServiceManager.checkService(matchCommand, caller.user, caller.subject)
      if (s != null) {
        return InterceptResult(InterceptedReason("权限不足或功能未启用"))
      }
    }
    val reason = CommandInterceptorManager.emitInterceptBeforeCall(message, caller)
    return if (reason != null) {
      InterceptResult(InterceptedReason(reason))
    } else {
      super.interceptBeforeCall(message, caller)
    }
  }
}

fun extraCommandName(message: Message): String {
  val contentToString = message.contentToString().trim().split(" ")
  return contentToString[0]
}

// 中文说明：定义 TempMessageIgnoreType 类型，用于封装本模块的数据或处理行为。
enum class TempMessageIgnoreType {
  NONE,
  ONLY_SERVICE_GROUP,
  ALL
}
