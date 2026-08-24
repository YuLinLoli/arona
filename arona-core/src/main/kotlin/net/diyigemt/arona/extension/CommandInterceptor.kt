/**
 * 文件说明：本文件属于 命令拦截与扩展机制。
 * 具体职责：围绕 CommandInterceptor 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.extension

import net.diyigemt.arona.Arona
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.parse.CommandCall
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChainBuilder

@OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
// 中文说明：定义 CommandInterceptor 接口，约定相关实现需要提供的能力。
interface CommandInterceptor {
  val level: Int
  fun interceptCall(call: CommandCall): Boolean = true
  fun interceptBeforeCall(message: Message, caller: CommandSender): String? = null
  fun registerInterceptor() {
    CommandInterceptorManager.registerItem(this)
  }
  fun overrideDefaultCommand(commandBase: String, override: String, caller: CommandSender) {
    val builder = MessageChainBuilder()
    builder.add("$commandBase $override")
    Arona.runSuspend {
      CommandManager.executeCommand(caller, builder.build())
    }
  }
}
