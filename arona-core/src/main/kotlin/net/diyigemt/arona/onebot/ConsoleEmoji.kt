package net.diyigemt.arona.onebot

/**
 * 黑窗口控制台表情显示支持检测与降级。
 *
 * 经典 Windows conhost 无法显示彩色 emoji（如 😂），打印聊天记录/发送人名/群名时会把 emoji
 * 替换为 [emoji:编号]（Unicode 码点十六进制，如 😂 -> [emoji:1F602]）；
 * Windows Terminal / ConEmu 等现代终端保留原样。
 * 可用 -Darona.console.emoji=true|false 强制指定，如 -Darona.console.emoji=false。
 */
object ConsoleEmoji {

  /** 当前控制台是否支持显示 emoji */
  @Volatile
  var supported: Boolean = true

  /** 启动时调用，按运行环境检测控制台是否支持 emoji */
  fun init() {
    supported = detect()
  }

  /** 不支持 emoji 时把文本中的 emoji 替换为 [emoji:码点]，支持时原样返回 */
  fun sanitize(text: String): String {
    if (supported || text.isEmpty()) return text
    val builder = StringBuilder(text.length + 16)
    var index = 0
    while (index < text.length) {
      val cp = text.codePointAt(index)
      val width = Character.charCount(cp)
      val next = if (index + width < text.length) text.codePointAt(index + width) else -1
      when {
        // 变体选择符/零宽连接/围按键帽等组合字符随 emoji 一起省略
        cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 -> Unit
        isEmoji(cp, next) -> builder.append("[emoji:").append(Integer.toHexString(cp).uppercase()).append(']')
        else -> builder.appendCodePoint(cp)
      }
      index += width
    }
    return builder.toString()
  }

  private fun isEmoji(cp: Int, next: Int): Boolean = when {
    // 补充平面表情（彩色 emoji 主体，如 😂）
    cp in 0x1F000..0x1FAFF -> true
    // BMP 符号：显式 emoji 呈现（后跟 U+FE0F）或常见默认 emoji 呈现的彩色符号
    cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ->
      next == 0xFE0F || cp in BMP_EMOJI_PRESENTATION
    else -> false
  }

  /** BMP 范围内、默认按 emoji 呈现且 conhost 无法以文本字形显示的常见彩色符号 */
  private val BMP_EMOJI_PRESENTATION = setOf(
    0x2705, 0x2728, 0x274C, 0x274E, 0x2753, 0x2754, 0x2755, 0x2757,
    0x2764, 0x2795, 0x2796, 0x2797, 0x2B50, 0x2B55,
  )

  private fun detect(): Boolean {
    // 显式开关
    System.getProperty("arona.console.emoji")?.let {
      return it.equals("true", ignoreCase = true) || it == "1"
    }
    // 输出被重定向（管道/文件）时不降级，保留 emoji 便于落盘
    if (System.console() == null) return true
    val os = System.getProperty("os.name", "").lowercase()
    if (os.contains("win")) {
      // Windows Terminal / ConEmu 等现代终端支持彩色 emoji；经典 conhost 不支持
      if (System.getenv("WT_SESSION") != null) return true
      if (System.getenv("ConEmuPID") != null) return true
      return false
    }
    return true
  }
}