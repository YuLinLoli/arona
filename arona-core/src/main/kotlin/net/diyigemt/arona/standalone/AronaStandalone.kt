package net.diyigemt.arona.standalone

import net.diyigemt.arona.onebot.OneBotApplication
import net.diyigemt.arona.onebot.OneBotConfigLoader
import net.diyigemt.arona.runtime.RuntimePaths
import net.diyigemt.arona.runtime.RuntimeServices
import java.util.concurrent.CountDownLatch

object AronaStandalone {
  @JvmStatic
  fun main(args: Array<String>) {
    val configFile = args.firstOrNull { it.startsWith("--config=") }
      ?.substringAfter("=")
      ?.let { java.io.File(it) }
      ?.toPath()
      ?: OneBotConfigLoader.defaultFile()
    RuntimeServices.dataRoot = RuntimePaths.prepareStandaloneRoot().resolve("data")
    val config = OneBotConfigLoader.load(configFile)
    val application = OneBotApplication(config)
    application.start()
    Runtime.getRuntime().addShutdownHook(Thread { application.stop() })
    println("Arona standalone started")
    println("Config: ${configFile.toAbsolutePath()}")
    CountDownLatch(1).await()
  }
}


