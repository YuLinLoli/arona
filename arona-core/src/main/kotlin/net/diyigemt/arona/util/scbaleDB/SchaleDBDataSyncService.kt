/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 SchaleDBDataSyncService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util.scbaleDB

import com.google.gson.Gson
import net.diyigemt.arona.db.DB
import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.data.schaledb.MD5
import net.diyigemt.arona.entity.schaleDB.*
import net.diyigemt.arona.quartz.QuartzProvider
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.service.AronaQuartzService
import okio.ByteString.Companion.toByteString
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jsoup.Jsoup
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 *@Author hjn
 *@Create 2022/8/20
 */
// 中文说明：定义 SchaleDBDataSyncService 对象，集中提供本文件的共享功能。
object SchaleDBDataSyncService : AronaQuartzService{
  override var jobKey: JobKey? = null
  lateinit var birthdayJobKey: JobKey
  override val id: Int = 19
  override val name: String = "数据同步服务"
  override var enable: Boolean = true
  private const val SchaleDBDataSyncServiceJobKey = "SchaleDBDataSyncService"
  private const val BirthdayJobKey = "Birthday"

  /** GitHub 原始数据源(优先, 结构与本地模型一致) */
  private const val gitHub = "https://raw.githubusercontent.com/SchaleDB/SchaleDB/main/"
  /** schale.gg 官网数据源(备用) */
  private const val schaleGG = "https://schale.gg/"
  /** 国内镜像源(最后兜底) */
  private const val CN = "https://schaledb.brightsu.cn/"
  /** 新版 SchaleDB 仓库中保存当前卡池/活动/总力战的文件 */
  private const val common = "data/config.json"
  private const val student = "data/cn/students.min.json"
  private const val localization = "data/cn/localization.json"
  private const val raid = "data/cn/raids.min.json"
  // TODO: resString 是共享可变状态, 定时同步与手动触发并发执行时日志字符串会互相交错, 建议改为方法内局部变量
  private var resString = ""

  class SchaleDBDataSyncJob : Job{
    override fun execute(context: JobExecutionContext?) = getData()

    fun getData(){
      // 每个数据源独立同步, 单个失败不影响其他数据
      runCatching {
        SchaleDBUtil.studentItem = getStudentData()
      }.onFailure {
        RuntimeLog.warning("学生数据同步失败: ${it.message}")
      }
      runCatching {
        SchaleDBUtil.localizationItem = getLocalizationData()
      }.onFailure {
        RuntimeLog.warning("本地化数据同步失败: ${it.message}")
      }
      runCatching {
        SchaleDBUtil.raidItem = getRaidData()
      }.onFailure {
        RuntimeLog.warning("总力战数据同步失败: ${it.message}")
      }
      runCatching {
        SchaleDBUtil.commonItem = getCommonData()
      }.onFailure {
        RuntimeLog.warning("当前卡池/活动数据同步失败: ${it.message}")
      }
    }
  }

  class BirthdayJob : Job{
    private val formatter = DateTimeFormatter.ofPattern("yyyy/M/d")
    override fun execute(context: JobExecutionContext?) = getBirthdayList()

    fun getBirthdayList() {
      SchaleDBUtil.birthdayList.clear()
      SchaleDBUtil.studentItem.forEach {
        runCatching {
          val date = LocalDate.parse(LocalDate.now().year.toString() + "/" + it.BirthDay, formatter)
          if (date.isAfter(LocalDate.now()) && date.isBefore(LocalDate.now().plusWeeks(1))){
            SchaleDBUtil.birthdayList.add(Birthday(it.Name, date))
          }
        }.onFailure {  }
      }
    }
  }

  enum class JSONDataType{
    COMMON,STUDENT,LOCALIZATION,RAID
  }

  enum class DataBaseType{
    STUDENT,EVENT,RAID
  }

  enum class RemoteType{
    GITHUB, MIRROR
  }

  /**
   * 获取当前卡池/活动/总力战数据。
   * 新版 SchaleDB 仓库已移除 common.min.json, 改用 data/config.json 中的 Regions 字段。
   */
  private fun getCommonData(): CommonDAO {
    val fetched = fetchRemoteContent(common, JSONDataType.COMMON.name)
      ?: return CommonDAO().toModel(CommonDAO())
    val (res, isGitHub) = fetched
    val config = runCatching { Gson().fromJson(res, SchaleDBConfig::class.java) }.getOrNull()
    if (config == null || config.Regions.isEmpty()) {
      RuntimeLog.warning("解析数据源: COMMON 失败, 保留旧数据")
      return CommonDAO().toModel(CommonDAO())
    }
    val md5 = MessageDigest.getInstance("MD5").digest(res.toByteArray()).toByteString().hex()
    val query = runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) { MD5.select(MD5.name eq JSONDataType.COMMON.name).first() }
    }.getOrNull()
    val dao = CommonDAO(regions = config.Regions.map { it.toRegions() })
    dao.sendToDataBase()
    resString = "Source: ${JSONDataType.COMMON.name}"
    if (query?.getOrNull(MD5.name) != null) {
      if (query.getOrNull(MD5.md5) == md5) {
        resString += " already up to date."
      } else {
        updateMD5(JSONDataType.COMMON.name, if (isGitHub) RemoteType.GITHUB.name else RemoteType.MIRROR.name, md5)
        resString += " updated."
      }
    } else {
      updateMD5(JSONDataType.COMMON.name, if (isGitHub) RemoteType.GITHUB.name else RemoteType.MIRROR.name, md5)
      resString += " added."
    }
    RuntimeLog.info(resString)
    return dao.toModel(dao)
  }

  private fun getStudentData(): StudentDAO = getSchaleDBData(student, JSONDataType.STUDENT.name)

  private fun getLocalizationData(): LocalizationDAO = getSchaleDBData(localization, JSONDataType.LOCALIZATION.name)

  private fun getRaidData() : RaidDAO = getSchaleDBData(raid, JSONDataType.RAID.name)

  /**
   * 依次从 GitHub / schale.gg / 国内镜像拉取数据, 并校验返回内容是否为合法 JSON。
   * 返回 (内容, 是否来自 GitHub) 或 null。
   */
  private fun fetchRemoteContent(url: String, dataType: String): Pair<String, Boolean>? {
    val sources = listOf(gitHub to true, schaleGG to true, CN to false)
    for ((base, isGitHub) in sources) {
      val body = try {
        Jsoup.connect(base + url)
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/102.0.0.0 Safari/537.36")
          .header("Content-Type", "application/json;charset=UTF-8")
          .maxBodySize(0)
          .timeout(15000)
          .ignoreContentType(true)
          .execute()
          .body()
      } catch (e: Exception) {
        RuntimeLog.warning("数据源: $dataType 从 $base 获取失败: ${e.message}")
        continue
      }
      val trimmed = body.removePrefix("\uFEFF").trimStart()
      if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        RuntimeLog.warning("数据源: $dataType 在 $base 返回的不是合法JSON, 尝试下一个源")
        continue
      }
      return trimmed to isGitHub
    }
    RuntimeLog.warning("获取数据源: $dataType 全部失败, 请检查网络连接")
    return null
  }

  private inline fun <reified T : BaseDAO> getSchaleDBData(url : String, dataType : String) : T{
    val default = T::class.java.getDeclaredConstructor().newInstance()
    val fetched = fetchRemoteContent(url, dataType) ?: return default.toModel(default)
    val (res, isGitHub) = fetched

    resString = ""
    var isUpdate = false
    val md5 = MessageDigest.getInstance("MD5").digest(res.toByteArray()).toByteString().hex()
    val query = runCatching {
      DataBaseProvider.query(DB.DATA.ordinal) { MD5.select(MD5.name eq dataType).first() }
    }.getOrNull()
    // 国内镜像源的学生数据可能是 {id: {...}} 对象格式, 需要转成列表
    val dao: T? = when {
      T::class.java == StudentDAO::class.java && res.trimStart().startsWith("{") -> {
        val map = runCatching {
          Gson().fromJson<Map<String, StudentDAOItem>>(res, object : com.google.gson.reflect.TypeToken<Map<String, StudentDAOItem>>() {}.type)
        }.getOrNull()
        if (map == null) null else (StudentDAO().apply { addAll(map.values) } as T)
      }
      else -> runCatching { Gson().fromJson(res, T::class.java) }.getOrNull()
    }
    //解析失败时返回模板对象的默认值(从数据库读取旧数据)
    if (dao == null){
      RuntimeLog.warning("解析数据源: $dataType 失败, 保留旧数据")
      return default.toModel(default)
    }
    resString += "Source: $dataType"
    if (query?.getOrNull(MD5.name) != null){
      when(query.getOrNull(MD5.remote)){
        RemoteType.GITHUB.name -> apply {
          resString += " from GitHub"
          if (query.getOrNull(MD5.md5) != md5 && isGitHub){
            kotlin.runCatching { dao.sendToDataBase() }.onSuccess {
              updateMD5(dataType, RemoteType.GITHUB.name, md5)
            }
            isUpdate = true
          } else resString += " already up to date."
        }
        else -> apply {
          resString += " from mirror"
          if (query.getOrNull(MD5.md5) != md5){
            kotlin.runCatching { dao.sendToDataBase() }.onSuccess {
              updateMD5(dataType, RemoteType.MIRROR.name, md5)
            }
            isUpdate = true
          } else resString += " already up to date."
        }
      }
    }else{
      dao.sendToDataBase()
      if (isGitHub) updateMD5(dataType, RemoteType.GITHUB.name, md5)
      else updateMD5(dataType, RemoteType.MIRROR.name, md5)
      isUpdate = true
    }
    RuntimeLog.info(resString)

    if (isUpdate) return dao

    return dao.toModel(dao)
  }

  private fun updateMD5(name : String, remote : String, md5 : String){
    val query = DataBaseProvider.query(DB.DATA.ordinal) { MD5.select(MD5.name eq name).toList() } ?: return
    if (query.isEmpty()){
      resString += " updated."
      DataBaseProvider.query(DB.DATA.ordinal) {
        MD5.insert {
          it[MD5.name] = name
          it[MD5.remote] = remote
          it[MD5.md5] = md5
        }
      }
    }
    else{
      resString += " added."
      DataBaseProvider.query(DB.DATA.ordinal) {
        MD5.update({ MD5.name eq name }) {
          it[MD5.remote] = remote
          it[MD5.md5] = md5
        }
      }
    }
  }

  override fun init() = registerService()

  // TODO start cron task
  override fun enableService() {
    //数据同步，程序启动时立即执行，每小时刷新一次
    jobKey = QuartzProvider.createCronTask(
      SchaleDBDataSyncJob::class.java,
      "0 0 0/1 * * ? *",
      SchaleDBDataSyncServiceJobKey,
      SchaleDBDataSyncServiceJobKey
    ).first
    QuartzProvider.triggerTask(jobKey!!)

    //生日计算，程序启动5秒后进行，每天0点刷新
    birthdayJobKey = QuartzProvider.createCronTask(
      BirthdayJob::class.java,
      "0 0 0 * * ? *",
      BirthdayJobKey,
      BirthdayJobKey
    ).first
    QuartzProvider.createSimpleDelayJob(5){
      QuartzProvider.triggerTask(birthdayJobKey)
    }
  }

  // TODO cancel cron task
  override fun disableService() {
    super.disableService()
  }
}

// 中文说明：定义 SchaleDBConfig 类型, 对应新版 SchaleDB 仓库 data/config.json 的根结构。
data class SchaleDBConfig(
  val Regions: List<ConfigRegion> = emptyList()
)

// 中文说明：定义 ConfigRegion 类型, 对应 config.json 中单个服务器的当前活动数据。
data class ConfigRegion(
  val Name: String = "",
  val CurrentGacha: List<ConfigGacha> = emptyList(),
  val CurrentEvents: List<ConfigEvent> = emptyList(),
  val CurrentRaid: List<ConfigRaid> = emptyList()
) {
  fun toRegions(): Regions = Regions(
    abbreviation = when (Name.lowercase()) {
      "jp", "jpn" -> "JPN"
      "gl", "glb", "global" -> "GLB"
      "cn" -> "CN"
      else -> null
    },
    current_gacha = CurrentGacha.map { CurrentGacha(it.characters, it.start, it.end) },
    current_events = CurrentEvents.map { CurrentEvents(it.event, it.start, it.end) },
    current_raid = CurrentRaid.map { CurrentRaid(it.raid, it.terrain, it.start, it.end) }
  )
}

// 中文说明：定义 ConfigGacha 类型, 对应 config.json 中的卡池条目。
data class ConfigGacha(
  val characters: List<Int> = emptyList(),
  val start: Long = 0,
  val end: Long = 0
)

// 中文说明：定义 ConfigEvent 类型, 对应 config.json 中的活动条目。
data class ConfigEvent(
  val event: Int = 0,
  val start: Long = 0,
  val end: Long = 0
)

// 中文说明：定义 ConfigRaid 类型, 对应 config.json 中的总力战条目。
data class ConfigRaid(
  val raid: Int = 0,
  val terrain: String = "",
  val start: Long = 0,
  val end: Long = 0
)
