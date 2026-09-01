/**
 * 文件说明：kivo.wiki 学生数据源。
 * 具体职责：按服务器 × 星级(1/2/3) 从 kivo.wiki 拉取全量已实装学生列表，
 *           学生完整名格式为 given_name_cn（skin_cn），支持内存 + 磁盘双缓存；
 *           启动时检查三服学生是否有更新（对比数量/名单），新学生自动预热进卡池并写回缓存。
 */
package net.diyigemt.arona.gacha

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.interfaces.InitializedFunction
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.GeneralUtils
import net.diyigemt.arona.util.NetworkUtil
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** kivo.wiki 学生数据源: 三服 × 1/2/3 星分类拉取, 内存 + 磁盘缓存。 */
object KivoStudentSource : InitializedFunction() {

  private const val BASE_URL = "https://api.kivo.wiki/api/v1/data/students/"
  private const val PAGE_SIZE = 1000
  private const val CACHE_TTL_MS = 30 * 60 * 1000L
  private const val CACHE_VERSION = 1

  /** kivo 学生条目: name 即完整名(given_name_cn（skin_cn）), star 即所属星级 */
  data class KivoStudent(
    val id: Long,
    val name: String,
    val descName: String,
    val star: Int,
    /** 头像地址(//static.kivo.wiki/...), 用于抽卡结果图片渲染 */
    val avatar: String = "",
  )

  private data class CacheEntry(
    val students: Map<Int, List<KivoStudent>>,
    val expireAt: Long,
  )

  private val cache = ConcurrentHashMap<ServerLocale, CacheEntry>()

  /** 测试注入点: 替换为本地构造数据即可离线验证建池逻辑 */
  internal var fetcher: (ServerLocale) -> Map<Int, List<KivoStudent>> = { server -> fetchFromNetwork(server) }

  /** 测试注入点: 启动刷新检查时使用的拉取函数(默认强制走网络, 绕过内存缓存) */
  internal var refresher: (ServerLocale) -> Map<Int, List<KivoStudent>> = { server -> fetchUncached(server) }

  /** 获取某服务器按星级分类的学生列表(带内存缓存) */
  fun fetchStudents(server: ServerLocale): Map<Int, List<KivoStudent>> = fetcher(server)

  internal fun fetchFromNetwork(server: ServerLocale): Map<Int, List<KivoStudent>> {
    cache[server]?.takeIf { it.expireAt > System.currentTimeMillis() }?.let { return it.students }
    val result = fetchUncached(server)
    cache[server] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
    return result
  }

  internal fun fetchUncached(server: ServerLocale): Map<Int, List<KivoStudent>> {
    val isInstall = when (server) {
      ServerLocale.JP -> "is_install"
      ServerLocale.CN -> "is_install_cn"
      ServerLocale.GLOBAL -> "is_install_global"
    }
    return buildMap {
      for (rarity in 1..3) {
        this[rarity] = fetchRarity(isInstall, rarity)
      }
    }
  }

  /** 启动检查(后台线程, 不阻塞启动): 先用磁盘缓存预热内存, 再刷新三服学生并缓存新学生 */
  override fun init() {
    Thread {
      runCatching {
        val file = defaultCacheFile()
        // 先用磁盘缓存预热内存, 保证离线时也能直接建池
        loadCache(file).forEach { (serverName, students) ->
          ServerLocale.values().firstOrNull { it.name == serverName }?.let { server ->
            cache[server] = CacheEntry(students, System.currentTimeMillis() + CACHE_TTL_MS)
          }
        }
        refreshAndCache(file)
      }.onFailure { RuntimeLog.warning("kivo 学生数据启动检查失败: ${it.message}") }
    }.apply {
      isDaemon = true
      name = "arona-kivo-refresh"
      start()
    }
  }

  /** 单服刷新结果摘要 */
  internal data class KivoRefreshReport(
    val server: ServerLocale,
    val oldCount: Int,
    val newCount: Int,
    val newStudentIds: List<Long>,
    val newStudents: List<KivoStudent>,
  )

  /** 对三服逐服拉取最新数据, 与磁盘缓存对比, 缓存新学生并预热内存缓存 */
  internal fun refreshAndCache(cacheFile: File): List<KivoRefreshReport> {
    val previous = loadCache(cacheFile)
    val reports = mutableListOf<KivoRefreshReport>()
    var changed = false
    ServerLocale.values().forEach { server ->
      val fresh = runCatching { refresher(server) }.getOrElse {
        RuntimeLog.warning("kivo ${server.serverName} 学生数据刷新失败: ${it.message}")
        return@forEach
      }
      val freshIds = fresh.values.flatten().map { it.id }.toSet()
      val oldIds = previous[server.name]?.values?.flatten()?.map { it.id }?.toSet().orEmpty()
      val newIds = freshIds - oldIds
      val newStudents = fresh.values.flatten().filter { it.id in newIds }
      reports += KivoRefreshReport(server, oldIds.size, freshIds.size, newIds.toList(), newStudents)
      // 预热内存缓存: 后续抽卡建池直接使用最新数据
      cache[server] = CacheEntry(fresh, System.currentTimeMillis() + CACHE_TTL_MS)
      if (newStudents.isNotEmpty() || oldIds.size != freshIds.size) {
        changed = true
        RuntimeLog.info(
          "kivo ${server.serverName} 学生数据${if (newStudents.isEmpty()) "数量变化" else "更新"}: " +
            "${oldIds.size} -> ${freshIds.size}, 新学生: " +
            newStudents.joinToString { "${it.name}(${it.id})" }
        )
      }
    }
    if (changed || previous.isEmpty()) {
      saveCache(cacheFile, currentSnapshot())
    }
    return reports
  }

  /** 把当前内存缓存写成磁盘缓存(供下一次启动对比) */
  private fun saveCache(file: File, servers: Map<String, Map<Int, List<KivoStudent>>>) {
    runCatching {
      file.parentFile?.mkdirs()
      val data = KivoCacheData(CACHE_VERSION, System.currentTimeMillis(), servers)
      file.writeText(Gson().toJson(data))
    }.onFailure { RuntimeLog.warning("kivo 学生数据缓存写入失败: ${it.message}") }
  }

  private fun loadCache(file: File): Map<String, Map<Int, List<KivoStudent>>> {
    if (!file.isFile || file.length() == 0L) return emptyMap()
    return runCatching {
      val data = Gson().fromJson(file.readText(), KivoCacheData::class.java)
      if (data?.version != CACHE_VERSION) emptyMap() else data.servers
    }.onFailure { RuntimeLog.warning("kivo 学生数据缓存读取失败: ${it.message}") }.getOrDefault(emptyMap())
  }

  private fun currentSnapshot(): Map<String, Map<Int, List<KivoStudent>>> =
    cache.entries.associate { (server, entry) -> server.name to entry.students }

  private fun defaultCacheFile(): File = GeneralUtils.localImageFile("/gacha/kivo-cache.json")

  private fun fetchRarity(isInstall: String, rarity: Int): List<KivoStudent> {
    val result = mutableListOf<KivoStudent>()
    var page = 1
    while (true) {
      val json = runCatching {
        NetworkUtil.request(
          Jsoup.connect("$BASE_URL?page=$page&page_size=$PAGE_SIZE&$isInstall=true&rarity=$rarity")
            .header("accept", "application/json, text/plain, */*")
            .header("accept-language", "zh-CN,zh;q=0.9")
            .header("referer", "https://ba.kivo.wiki/")
            .timeout(20_000)
        ).get().text()
      }.onFailure { RuntimeLog.error("kivo 学生数据请求失败: ${it.message}") }.getOrNull() ?: break
      val pageData = parsePage(json, rarity)
      result += pageData.first
      if (page >= pageData.second) break
      page++
    }
    return result
  }

  /** 解析单页响应, 返回 (学生列表, max_page); 供测试直接注入 JSON */
  internal fun parsePage(json: String, rarity: Int): Pair<List<KivoStudent>, Int> {
    val response = Gson().fromJson(json, KivoPageResponse::class.java)
    if (response.code != 2000 || response.data?.students == null) {
      throw IllegalStateException("kivo 接口异常: code=${response.code} msg=${response.message}")
    }
    val students = response.data.students.mapNotNull { it.toKivoStudent(rarity) }
    return students to (response.data.maxPage ?: 1)
  }

  private fun KivoStudentJson.toKivoStudent(rarity: Int): KivoStudent? {
    // 完整名格式: given_name_cn（skin_cn）, 缺省时回落 given_name / family_name+given_name
    val base = (givenNameCn ?: givenName ?: "").trim()
    val name = if (base.isNotBlank()) {
      if (skinCn.isNullOrBlank()) base else "$base（${skinCn}）"
    } else {
      ((familyName ?: "") + (givenName ?: "")).trim()
    }
    if (name.isBlank()) return null
    return KivoStudent(
      id = id,
      name = name,
      descName = (givenNameJp ?: "").ifBlank { givenName ?: "" },
      star = rarity,
      avatar = avatar.orEmpty(),
    )
  }

  internal data class KivoPageResponse(
    val code: Int = -1,
    val data: KivoPageData? = null,
    val message: String? = null,
  )

  internal data class KivoPageData(
    @SerializedName("max_page") val maxPage: Int? = null,
    val students: List<KivoStudentJson>? = null,
  )

  internal data class KivoStudentJson(
    val id: Long = 0,
    @SerializedName("family_name") val familyName: String? = null,
    @SerializedName("given_name") val givenName: String? = null,
    @SerializedName("given_name_jp") val givenNameJp: String? = null,
    @SerializedName("given_name_cn") val givenNameCn: String? = null,
    @SerializedName("skin_cn") val skinCn: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
  )

  /** 磁盘缓存结构 */
  internal data class KivoCacheData(
    val version: Int = CACHE_VERSION,
    val savedAt: Long = 0,
    val servers: Map<String, Map<Int, List<KivoStudent>>> = emptyMap(),
  )
}