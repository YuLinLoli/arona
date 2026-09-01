package net.diyigemt.arona.gacha

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.GameKeeUtil
import net.diyigemt.arona.util.NetworkUtil
import org.jsoup.Jsoup
import java.io.File

/**
 * 新抽卡体系(与旧抽卡逻辑分离): GameKee 当期卡池数据源。
 *
 * 请求 https://www.gamekee.com/v1/wiki/indexV2, 取 data 里 module.name == "卡池" 的 list;
 * 该 list 是以 server_id 字符串为 key 的对象: "15"=日服, "16"=国服, "17"=国际服。
 * 第一步只负责把三个服当期卡池的角色对象抓下来合并成一个 List 备用, 不做群内响应。
 */
object GameKeeGachaPoolSource {

  private const val INDEX_V2_URL = "https://www.gamekee.com/v1/wiki/indexV2"
  private const val POOL_MODULE_NAME = "卡池"
  private const val REFERER = "https://www.gamekee.com/ba/"

  /** 服务器 */
  enum class GachaServer(val serverId: Int, val displayName: String) {
    JP(15, "日服"),
    CN(16, "国服"),
    GLOBAL(17, "国际服");

    companion object {
      fun fromServerId(serverId: Int): GachaServer? = values().firstOrNull { it.serverId == serverId }
    }
  }

  /** 当期卡池角色(保留 raw 原始 JSON 备用, 后续建池/推送可能还需要其它字段) */
  data class GachaCharacter(
    val id: Long,
    val name: String,
    val nameAlias: String,
    val serverId: Int,
    val star: Int,
    val startAt: Long,
    val endAt: Long,
    val sort: Int,
    val icon: String,
    val imageList: String,
    val linkUrl: String,
    val tagId: String,
    val raw: JsonObject,
  )

  /** 角色 + 所属服务器, 三个服合并成一个 List 时用 */
  data class PoolEntry(
    val server: GachaServer,
    val character: GachaCharacter,
  )

  /** 拿取三个服务器当期卡池并合并为一个 List */
  fun fetchCurrentPools(): List<PoolEntry> {
    val json = requestIndexV2() ?: throw IllegalStateException("GameKee indexV2 请求失败")
    return parsePools(json)
  }

  /** 只拿某个服务器的当期卡池 */
  fun fetchPool(server: GachaServer): List<GachaCharacter> =
    fetchCurrentPools().filter { it.server == server }.map { it.character }

  /** 按角色 id 查找图片缓存: 优先匹配 <id>.<后缀>, 兼容旧命名 <id>-<角色名>.<后缀> */
  fun findCachedImage(characterId: Long, directory: File): File? {
    val id = characterId.toString()
    val files = directory.listFiles()?.filter { it.isFile } ?: return null
    return files.firstOrNull { it.name.startsWith("$id.") }
      ?: files.firstOrNull { it.name.startsWith("$id-") }
  }

  /** 上传头图前先查缓存, 命中直接返回; 未命中则下载卡池图(image_list)并保存为 <角色id>.<后缀>; 失败返回 null */
  fun downloadCharacterImage(character: GachaCharacter, directory: File): File? =
    downloadToCache(character.imageList, character.id, directory, "卡池图")

  /** 通用下载: 先查 <id> 缓存, 未命中则按 url 下载并保存为 <角色id>.<后缀>(复用 GameKeeUtil.gameKeeHeaders() 的请求头) */
  private fun downloadToCache(url: String, id: Long, directory: File, label: String): File? {
    findCachedImage(id, directory)?.let { return it }
    val normalized = normalizeImageUrl(url)
    if (normalized.isBlank()) return null
    runCatching { directory.mkdirs() }
    val suffix = normalized.substringBefore("?").substringAfterLast(".", "png")
      .takeIf { it.length in 2..5 }?.let { ".$it" } ?: ".png"
    val file = File(directory, "$id$suffix")
    if (file.isFile && file.length() > 0) return file
    return runCatching {
      val response = NetworkUtil.request(Jsoup.connect(normalized))
        .headers(GameKeeUtil.gameKeeHeaders(REFERER))
        .header("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .ignoreContentType(true)
        .maxBodySize(15 * 1024 * 1024)
        .execute()
      response.bodyStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
      if (file.isFile && file.length() > 0) file else null
    }.onFailure { RuntimeLog.warning("下载${label}失败 $normalized: ${it.message}") }.getOrNull()
  }

  /** imageList 可能是 // 开头的协议相对地址 */
  private fun normalizeImageUrl(url: String): String = when {
    url.isBlank() -> ""
    url.startsWith("//") -> "https:$url"
    else -> url
  }

  /** 解析 indexV2 响应, 只保留三个已知服务器的角色 */
  internal fun parsePools(json: String): List<PoolEntry> {
    val root = JsonParser.parseString(json).asJsonObject
    val code = root.get("code")?.asInt ?: -1
    if (code != 0) {
      throw IllegalStateException("GameKee indexV2 返回错误: code=$code msg=${root.get("msg")?.asString}")
    }
    val data = root.getAsJsonArray("data") ?: throw IllegalStateException("GameKee indexV2 缺少 data")
    val poolModule = data.firstOrNull { module ->
      module.asJsonObject.getAsJsonObject("module")?.get("name")?.asString == POOL_MODULE_NAME
    } ?: throw IllegalStateException("GameKee indexV2 未找到卡池模块")
    val poolList = poolModule.asJsonObject.getAsJsonObject("list")
      ?: throw IllegalStateException("GameKee 卡池模块 list 为空")

    val result = mutableListOf<PoolEntry>()
    poolList.entrySet().forEach { (serverIdKey, characters) ->
      val serverId = serverIdKey.toIntOrNull() ?: return@forEach
      val server = GachaServer.fromServerId(serverId) ?: return@forEach
      if (characters !is JsonArray) return@forEach
      characters.forEach { element ->
        if (!element.isJsonObject) return@forEach
        val character = element.asJsonObject.toGachaCharacter() ?: return@forEach
        result += PoolEntry(server, character)
      }
    }
    return result
  }

  private fun requestIndexV2(): String? = runCatching {
    Jsoup.connect(INDEX_V2_URL)
      .ignoreContentType(true)
      .maxBodySize(15 * 1024 * 1024)
      .timeout(15_000)
      .headers(gameKeeHeaders())
      .get()
      .text()
  }.onFailure { RuntimeLog.error("GameKee indexV2 请求失败: ${it.message}") }.getOrNull()

  /**
   * 请求头按浏览器抓包配置。
   * 注: host/connection 由 JDK HttpURLConnection 自动处理且禁止手动设置, 故不在此列出;
   * access-token 抓包中为空值, 一并省略。
   */
  private fun gameKeeHeaders(): Map<String, String> = mapOf(
    "accept" to "application/json, text/plain, */*",
    "accept-encoding" to "gzip, deflate, br, zstd",
    "accept-language" to "zh-CN,zh;q=0.9,zh-Hans;q=0.8,und;q=0.7,zh-Hant;q=0.6,ja;q=0.5",
    "device-num" to "1",
    "dnt" to "1",
    "game-alias" to "ba",
    "lang" to "zh-cn",
    "referer" to "https://www.gamekee.com/ba/",
    "sec-ch-ua" to """ "Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150" """.trim(),
    "sec-ch-ua-mobile" to "?0",
    "sec-ch-ua-platform" to """ "Windows" """.trim(),
    "sec-fetch-dest" to "empty",
    "sec-fetch-mode" to "cors",
    "sec-fetch-site" to "same-origin",
    "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
    "x-requested-with" to "XMLHttpRequest",
  )

  /** 从单个角色 JSON 提取字段, id/name 缺失视为无效数据 */
  private fun JsonObject.toGachaCharacter(): GachaCharacter? {
    val id = get("id")?.asLong ?: return null
    val name = get("name")?.asString?.takeIf { it.isNotBlank() } ?: return null
    return GachaCharacter(
      id = id,
      name = name,
      nameAlias = get("name_alias")?.asString.orEmpty(),
      serverId = get("server_id")?.asInt ?: 0,
      star = get("star")?.takeUnless(JsonElement::isJsonNull)?.asString?.toIntOrNull() ?: 0,
      startAt = get("start_at")?.asLong ?: 0,
      endAt = get("end_at")?.asLong ?: 0,
      sort = get("sort")?.asInt ?: 0,
      icon = get("icon")?.asString.orEmpty(),
      imageList = get("image_list")?.asString.orEmpty(),
      linkUrl = get("link_url")?.asString.orEmpty(),
      tagId = get("tag_id")?.asString.orEmpty(),
      raw = this,
    )
  }
}
