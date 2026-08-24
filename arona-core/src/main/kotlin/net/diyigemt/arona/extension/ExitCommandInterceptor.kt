/**
 * 文件说明：本文件属于 命令拦截与扩展机制。
 * 具体职责：围绕 ExitCommandInterceptor 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.extension

import net.diyigemt.arona.Arona
import net.diyigemt.arona.interfaces.InitializedFunction
import net.diyigemt.arona.util.NetworkUtil
import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.UserCommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.parse.CommandCall
import net.mamoe.mirai.console.util.ConsoleExperimentalApi

@OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
// 中文说明：定义 ExitCommandInterceptor 对象，集中提供本文件的共享功能。
object ExitCommandInterceptor: CommandInterceptor {
  private val EXIT_COMMAND = listOf(
    "${CommandManager.commandPrefix}stop",
    "${CommandManager.commandPrefix}shutdown",
    "${CommandManager.commandPrefix}exit"
  )
  override val level: Int = 1

  override fun interceptCall(call: CommandCall): Boolean {
    if (call.caller is UserCommandSender) return true
    val calleeName = call.calleeName
    val valueArguments = call.valueArguments
    if (valueArguments.isEmpty() && EXIT_COMMAND.contains(calleeName)) {
      Arona.sendExitMessage()
      NetworkUtil.logoutInstance()
    }
    return true
  }
}
