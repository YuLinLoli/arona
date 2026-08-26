package net.diyigemt.arona.runtime

object RuntimeConfig {
  @Volatile var botId: Long = 0
  @Volatile var groups: List<Long> = emptyList()
  @Volatile var managers: List<Long> = emptyList()
  @Volatile var endWithSensei: String = "老师"
  @Volatile var uuid: String = ""
  @Volatile var proxyHost: String = ""
  @Volatile var proxyPort: Int = 0
  @Volatile var callMeEnabled: Boolean = true
}
