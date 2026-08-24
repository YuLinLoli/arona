/**
 * 文件说明：本文件属于 命令拦截与扩展机制。
 * 具体职责：围绕 CommandInterceptorManager 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.extension

import net.diyigemt.arona.command.CallMeCommand
import net.diyigemt.arona.interfaces.InitializedFunction
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.descriptor.ExperimentalCommandDescriptors
import net.mamoe.mirai.console.command.parse.CommandCall
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import net.mamoe.mirai.message.data.Message

@OptIn(ExperimentalCommandDescriptors::class, ConsoleExperimentalApi::class)
// 中文说明：定义 CommandInterceptorManager 对象，集中提供本文件的共享功能。
object CommandInterceptorManager: InitializedFunction() {
  private val ITEMS: MutableList<CommandInterceptor> = mutableListOf()
  fun registerItem(item: CommandInterceptor) {
    ITEMS.add(item)
    ITEMS.sortBy { it.level }
  }

  fun emitInterceptCall(call: CommandCall) {
    ITEMS.forEach {
      if (!it.interceptCall(call)) return@forEach
    }
  }

  fun emitInterceptBeforeCall(message: Message, caller: CommandSender): String? {
    ITEMS.forEach {
      val reason = it.interceptBeforeCall(message, caller)
      if (reason != null) return reason
    }
    return null
  }

  override fun init() {
    ExitCommandInterceptor.registerInterceptor()
    TempMessageInterceptor.registerInterceptor()
  }
}
