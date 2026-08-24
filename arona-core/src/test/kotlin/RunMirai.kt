/**
 * 文件说明：本文件属于 测试代码，用于验证相关模块的行为和边界情况。
 * 具体职责：围绕 RunMirai 提供对应的实现、数据结构或测试。
 */
package org.example.mirai.plugin

import net.diyigemt.arona.Arona
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.enable
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.load
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import java.io.File
import java.util.*

@OptIn(ConsoleExperimentalApi::class)
suspend fun main() {
  MiraiConsoleTerminalLoader.startAsDaemon()

  val pluginInstance = Arona

  pluginInstance.load() // 主动加载插件, Console 会调用 Dice.onLoad
  pluginInstance.enable() // 主动启用插件, Console 会调用 Dice.onEnable

  val properties = Properties().apply { File("account.properties").inputStream().use { load(it) } }

  val bot = MiraiConsole.addBot(properties.getProperty("id").toLong(), properties.getProperty("password"))
    .alsoLogin() // 登录一个测试环境的 Bot

  MiraiConsole.job.join()
}
