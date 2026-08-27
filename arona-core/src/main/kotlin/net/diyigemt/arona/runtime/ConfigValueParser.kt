package net.diyigemt.arona.runtime

/**
 * 把 /config 指令传入的字符串按目标类型解析成配置值。
 * 支持 Boolean / Int / Long / Float / Double / Char / String / List / Enum。
 * 列表支持 "1,2,3"、"[1, 2, 3]"、"1 2 3" 三种写法。
 */
object ConfigValueParser {

  fun parse(raw: String, current: Any?): Any? = when (current) {
    is Boolean -> parseBoolean(raw)
    is Int -> parseNumber(raw, "整数") { it.toInt() }
    is Long -> parseNumber(raw, "长整数") { it.toLong() }
    is Float -> parseNumber(raw, "浮点数") { it.toFloat() }
    is Double -> parseNumber(raw, "浮点数") { it.toDouble() }
    is Char -> raw.firstOrNull() ?: throw IllegalArgumentException("字符不能为空")
    is String -> raw
    is List<*> -> parseList(raw, current.firstOrNull() ?: 0L).let {
      if (current is MutableList<*>) it.toMutableList() else it
    }
    is Enum<*> -> parseEnum(raw, current)
    null -> raw
    else -> raw
  }

  fun parseBoolean(raw: String): Boolean = when (raw.trim().lowercase()) {
    "true", "1", "yes", "on", "是", "开" -> true
    "false", "0", "no", "off", "否", "关" -> false
    else -> throw IllegalArgumentException("无法解析为布尔值: $raw (可选 true/false 或 1/0)")
  }

  private fun parseNumber(raw: String, type: String, block: (String) -> Any): Any =
    try {
      block(raw.trim())
    } catch (_: NumberFormatException) {
      throw IllegalArgumentException("无法解析为$type: $raw")
    }

  private fun parseList(raw: String, sample: Any?): List<Any> {
    val cleaned = raw.trim().removePrefix("[").removeSuffix("]").trim()
    if (cleaned.isBlank()) return emptyList()
    val tokens = cleaned.split(Regex("[,，\\s]+")).filter(String::isNotBlank)
    return tokens.map { token ->
      when (sample) {
        is Long -> parseNumber(token, "长整数") { it.toLong() }
        is Int -> parseNumber(token, "整数") { it.toInt() }
        is Float -> parseNumber(token, "浮点数") { it.toFloat() }
        is Double -> parseNumber(token, "浮点数") { it.toDouble() }
        is Boolean -> parseBoolean(token)
        else -> token
      }
    }
  }

  private fun parseEnum(raw: String, current: Enum<*>): Enum<*> {
    val constants = current.javaClass.enumConstants
      ?: throw IllegalArgumentException("不是枚举类型")
    return constants.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
      ?: throw IllegalArgumentException("无法解析为枚举: $raw (可选: ${constants.joinToString("、") { it.name }})")
  }
}
