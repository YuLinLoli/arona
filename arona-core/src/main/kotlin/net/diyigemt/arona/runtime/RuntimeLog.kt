package net.diyigemt.arona.runtime

object RuntimeLog {
  /** 鲜绿色（ANSI 亮绿），用于黑窗口高亮提示类日志 */
  const val ANSI_BRIGHT_GREEN = "\u001b[92m"
  const val ANSI_RESET = "\u001b[0m"

  @Volatile var infoDelegate: ((String) -> Unit)? = null
  @Volatile var warningDelegate: ((String) -> Unit)? = null
  @Volatile var errorDelegate: ((String) -> Unit)? = null
  @Volatile var verboseDelegate: ((String) -> Unit)? = null

  fun info(message: String) = infoDelegate?.invoke(message) ?: println("[Arona] $message")
  fun warning(message: String) = warningDelegate?.invoke(message) ?: println("[Arona] $message")
  fun error(message: String) = errorDelegate?.invoke(message) ?: println("[Arona] $message")
  fun verbose(message: String) = verboseDelegate?.invoke(message) ?: println("[Arona] $message")

  fun error(message: () -> String) = error(message())
  fun verbose(message: () -> String) = verbose(message())

  /** 鲜绿色提示日志；插件模式（已挂载 Mirai logger）时退化为普通 info，避免 ANSI 乱码 */
  fun infoGreen(message: String) {
    if (infoDelegate != null) info(message)
    else println("$ANSI_BRIGHT_GREEN[Arona] $message$ANSI_RESET")
  }
}