package net.diyigemt.arona.standalone

import net.diyigemt.arona.cg.BuildConfig
import net.diyigemt.arona.onebot.ConsoleEmoji
import net.diyigemt.arona.onebot.OneBotApplication
import net.diyigemt.arona.onebot.OneBotConfigLoader
import net.diyigemt.arona.onebot.OneBotConsole
import net.diyigemt.arona.onebot.OneBotMessageSender
import net.diyigemt.arona.onebot.StandaloneBusinessHandler
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.gacha.KivoStudentSource
import net.diyigemt.arona.runtime.RuntimeConfig
import net.diyigemt.arona.standalone.StandaloneAronaConfig
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.runtime.RuntimePaths
import net.diyigemt.arona.runtime.RuntimeServices
import net.diyigemt.arona.standalone.commands.StandaloneActivityNotify
import net.diyigemt.arona.standalone.commands.StandaloneActivitySync
import net.diyigemt.arona.standalone.commands.StandaloneTarot
import net.diyigemt.arona.util.TarotDataUtil
import net.diyigemt.arona.util.GeneralUtils
import net.diyigemt.arona.util.ImageUtil
import net.diyigemt.arona.util.scbaleDB.SchaleDBDataSyncService
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Date

object AronaStandalone {
  @JvmStatic
  fun main(args: Array<String>) {
    enableUtf8Console()
    OneBotConsole.printBanner(BuildConfig.version)
    val configFile = args.firstOrNull { it.startsWith("--config=") }
      ?.substringAfter("=")
      ?.let { java.io.File(it).absoluteFile.toPath() }
      ?: OneBotConfigLoader.defaultFile()
    val aronaConfigFile = args.firstOrNull { it.startsWith("--arona-config=") }
      ?.substringAfter("=")
      ?.let { java.io.File(it).absoluteFile.toPath() }
      ?: StandaloneAronaConfig.defaultFile()
    RuntimeServices.isStandalone = true
    RuntimeServices.dataRoot = RuntimePaths.prepareStandaloneRoot().resolve("data")
    // 先加载业务配置(含热更新, 首次会从旧 onebot.yaml 迁移 groups/managers/notify)，再加载协议配置（清理 onebot.yaml 残留）
    StandaloneAronaConfig.init(aronaConfigFile)
    val config = OneBotConfigLoader.load(configFile)
    RuntimeConfig.botId = config.selfId
    RuntimeConfig.endWithSensei = "老师"
    RuntimeLog.info("initializing database...")
    runCatching { DataBaseProvider.start() }
      .onFailure { RuntimeLog.error("database init failed: ${it.message}") }
    runCatching { TarotDataUtil.ensureInitialized() }
      .onFailure { RuntimeLog.warning("塔罗牌数据初始化失败: ${it.message}") }
    runCatching { GeneralUtils.init() }
      .onFailure { RuntimeLog.warning("本地图片目录初始化失败: ${it.message}") }
    runCatching { ImageUtil.init() }
      .onFailure { RuntimeLog.warning("字体初始化失败: ${it.message}") }
    // 后台检查三服 kivo 学生数据是否有更新并预热卡池(新学生自动缓存)
    runCatching { KivoStudentSource.init() }
      .onFailure { RuntimeLog.warning("kivo 学生数据初始化失败: ${it.message}") }
    val commands = StandaloneCommandDispatcher(config)
    // 启用定时任务: 数据同步(每小时+启动立即执行)与每日活动推送
    // 注意: 服务本身已在 StandaloneServices 中登记, 这里直接启用 Quartz 任务, 不再重复注册
    runCatching { SchaleDBDataSyncService.enableService() }
      .onFailure { RuntimeLog.warning("启用数据同步服务失败: ${it.message}") }
    runCatching { StandaloneActivityNotify.enableService() }
      .onFailure { RuntimeLog.warning("启用活动推送失败: ${it.message}") }
    if (args.any { it == "--test-notify" }) {
      // 调试/验证用: 启动 20 秒后触发一次活动推送
      runCatching {
        QuartzProvider.createSingleTask(
          StandaloneActivityNotify.ActivityNotifyJob::class.java,
          Date(System.currentTimeMillis() + 20_000L),
          "TestNotify",
          "TestNotify",
        )
        RuntimeLog.info("测试推送已安排, 将在 20 秒后执行")
      }.onFailure { RuntimeLog.warning("安排测试推送失败: ${it.message}") }
    }
    startBackgroundSync()
    lateinit var application: OneBotApplication
    application = OneBotApplication(
      config,
      StandaloneBusinessHandler(config, commands.dispatcher) { event, source ->
        application.broadcastExcept(event.raw, source)
      },
    )
    application.start()
    RuntimeServices.messageSender = OneBotMessageSender({ application.firstConnection() }, config.selfId)
    Runtime.getRuntime().addShutdownHook(Thread { shutdown(application) })
    println("Arona standalone started")
    println("Config: ${configFile.toAbsolutePath()}")
    println("Arona 业务配置: ${aronaConfigFile.toAbsolutePath()}")
    println("日志文件: ${StandaloneLogFile.logDirectory()?.toAbsolutePath() ?: "未启用"}")
    CountDownLatch(1).await()
  }

  /** 优雅关闭: 停止 OneBot 连接、Quartz 定时任务、数据库、arona.yml 热重载监听与日志 */
  private fun shutdown(application: OneBotApplication) {
    RuntimeLog.info("正在关闭 Arona...")
    runCatching { application.stop() }
    runCatching { QuartzProvider.disable() }
    runCatching { DataBaseProvider.close() }
    runCatching { StandaloneAronaConfig.close() }
    StandaloneLogFile.close()
  }

  /** 启动后台同步任务: 活动日历写库、塔罗牌图片预下载 (学生/活动数据由上面的数据同步服务负责) */
  private fun startBackgroundSync() {
    Thread {
      StandaloneActivitySync.syncAll()
      runCatching { StandaloneTarot.downloadAllImages() }
        .onFailure { RuntimeLog.warning("塔罗牌图片预下载失败: ${it.message}") }
    }.apply {
      isDaemon = true
      name = "arona-background-sync"
      start()
    }
  }

  /** 让黑窗口以 UTF-8 输出，保证横幅/中文/表情符号等正常显示。 */
  private fun enableUtf8Console() {
    runCatching {
      ProcessBuilder("cmd", "/c", "chcp 65001 >nul").inheritIO().start().waitFor(3, TimeUnit.SECONDS)
    }
    runCatching {
      System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8"))
    }
    StandaloneLogFile.init()
    installColoredConsole()
    ConsoleEmoji.init()
  }

  /** 给黑窗口输出染色： [Arona]xxx / [OneBot xxx] 统一亮绿，WARNING:xxx / SLF4J:xxx 统一亮黄 */
  // TODO: ANSI 颜色无条件输出, 在经典 conhost 或输出重定向到文件时会留下裸转义码, 建议先检测终端能力
  private fun installColoredConsole() {
    val rawOut = System.out
    val rawErr = System.err
    System.setOut(ColoredPrintStream(TeePrintStream(rawOut)))
    System.setErr(ColoredPrintStream(TeePrintStream(rawErr)))
  }

  private class ColoredPrintStream(private val target: PrintStream) : PrintStream(target) {
    override fun print(x: String?) = target.print(colorize(x))
    override fun println(x: String?) = target.println(colorize(x))
    override fun println() = target.println()

    companion object {
      const val ANSI_BRIGHT_GREEN = "\u001b[92m"
      const val ANSI_BRIGHT_YELLOW = "\u001b[93m"
      const val ANSI_RESET = "\u001b[0m"
      private val SLF4J_LEVEL = Regex("""\s(INFO|DEBUG|WARN|ERROR|TRACE)\s""")

      fun colorize(line: String?): String? {
        if (line.isNullOrEmpty()) return line
        return when {
          // [Arona]xxx 与 [OneBot xxx] 统一亮绿
          line.startsWith("[Arona") || line.startsWith("[OneBot") ->
            "$ANSI_BRIGHT_GREEN$line$ANSI_RESET"
          // WARNING:xxx（中英文冒号）与 SLF4J:xxx（中英文冒号）统一亮黄
          line.contains("WARNING:") || line.contains("WARNING：") ||
            line.contains("SLF4J") || SLF4J_LEVEL.containsMatchIn(line) ->
            "$ANSI_BRIGHT_YELLOW$line$ANSI_RESET"
          else -> line
        }
      }
    }
  }
}
