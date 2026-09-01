/**
 * 文件说明：新版抽卡核心逻辑(参考 AronaBot gacha.ts), 与原 GachaUtil 分离。
 * 具体职责：基于 kivo.wiki 学生数据构建常驻池、GameKee 当期卡池构建 pickup 池,
 *          按官方概率抽卡(3★=0.7%pickup+2.3%常驻, 2★=18%, 1★=79%, 彩蛋0.05%),
 *          十连第10抽保底2星, 并提供文本结果。
 */
package net.diyigemt.arona.gacha

import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.gacha.GachaPity
import net.diyigemt.arona.db.gacha.GachaPityTable
import net.diyigemt.arona.db.gacha.GachaUserSetting
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.util.GachaUtil
import org.jetbrains.exposed.sql.and
import kotlin.random.Random

// 中文说明：定义 GachaV2Service 对象，集中提供本文件的共享功能。
object GachaV2Service {

  /** 星星符号, 下标即星级(1~3), 与参考实现 starString 一致 */
  private val starString = arrayOf("☆☆☆", "★☆☆", "★★☆", "★★★")

  /** 彩蛋角色(彩奈), 概率 0.05% */
  private const val EASTER_EGG_NAME = "彩奈"
  private const val EASTER_EGG_DESC_NAME = "Arona"
  private const val EASTER_EGG_DEV_NAME = "Arona"

  /** 200 抽保底: 距上次 pickup 累计满 200 抽时, 当次必出 pickup */
  private const val PITY_LIMIT = 200

  /** 抽卡用学生信息(轻量投影) */
  data class GachaStudent(
    val id: Long,
    val name: String,
    /** 罗马音名, 对应参考实现的 descName(PathName 优先, 回落 DevName) */
    val descName: String,
    /** 立绘文件名用(Student_Portrait_<devName>.png) */
    val devName: String,
    val star: Int,
    /** 头像地址, kivo 用 avatar, GameKee 当期角色用 icon; 用于抽卡结果图片渲染 */
    val avatar: String = "",
  )

  /** 单个服务器的抽卡池 */
  data class GachaV2Pool(
    val server: ServerLocale,
    /** 常驻池, key 为星级 1/2/3 */
    val common: Map<Int, List<GachaStudent>>,
    /** 当期 pickup 池(3 星, 含限定) */
    val pickup: List<GachaStudent>,
    val pickupStart: Long = 0,
    val pickupEnd: Long = 0,
  ) {
    val isEmpty: Boolean
      get() = common.values.all { it.isEmpty() } && pickup.isEmpty()
  }

  /** 单次抽卡结果 */
  data class GachaDrawResult(
    val star: Int,
    val name: String,
    val descName: String,
    val devName: String,
    /** 角色 id, 用于头像按角色缓存 */
    val id: Long = 0,
    /** 头像地址, 用于抽卡结果图片渲染 */
    val avatar: String = "",
    /** 彩蛋(彩奈)标记 */
    val custom: Boolean = false,
    val isPickup: Boolean = false,
  )

  /**
   * GameKee 当期卡池兜底池: 当 kivo 学生数据不可用导致抽卡池为空时使用。
   * 当期角色全部视为 3 星 pickup, 常驻池留空(避免把当期角色错误复制进 1/2 星)。
   */
  fun buildFallbackPool(server: ServerLocale): GachaV2Pool {
    val characters = runCatching { GameKeeGachaPoolSource.fetchPool(server.toGameKeeServer()) }
      .getOrNull().orEmpty()
    return buildFallbackPool(server, characters)
  }

  /** 供测试注入角色列表的兜底建池 */
  internal fun buildFallbackPool(server: ServerLocale, characters: List<GameKeeGachaPoolSource.GachaCharacter>): GachaV2Pool {
    if (characters.isEmpty()) {
      throw IllegalStateException("${server.serverName}抽卡池为空: kivo 学生数据与 GameKee 当期卡池均无数据, 请稍后再试")
    }
    val pickup = characters.map { it.toPickupStudent() }
    return GachaV2Pool(
      server = server,
      common = emptyMap(),
      pickup = pickup,
      pickupStart = characters.minOfOrNull { it.startAt } ?: 0,
      pickupEnd = characters.maxOfOrNull { it.endAt } ?: 0,
    )
  }

  private fun ServerLocale.toGameKeeServer(): GameKeeGachaPoolSource.GachaServer = when (this) {
    ServerLocale.JP -> GameKeeGachaPoolSource.GachaServer.JP
    ServerLocale.CN -> GameKeeGachaPoolSource.GachaServer.CN
    ServerLocale.GLOBAL -> GameKeeGachaPoolSource.GachaServer.GLOBAL
  }

  private fun GameKeeGachaPoolSource.GachaCharacter.toPickupStudent() = GachaStudent(
    id = id,
    name = name,
    descName = nameAlias.ifBlank { name },
    devName = "",
    star = 3,
    avatar = icon,
  )

  /** 单抽/十连的落点分类, 决定从哪个池子取角色 */
  internal enum class RollType { EASTER_EGG, PICKUP_3, COMMON_3, COMMON_2, COMMON_1 }

  /**
   * 构建指定服务器的抽卡池。
   * 常驻池: kivo.wiki 学生数据, 按星级(1/2/3) 分组;
   * pickup 池: GameKee 当期卡池(三服数据由调用方一次请求拿到), 全部视为 3 星。
   */
  fun buildPool(server: ServerLocale, gameKeePool: List<GameKeeGachaPoolSource.PoolEntry>? = null): GachaV2Pool {
    val common = KivoStudentSource.fetchStudents(server)
      .mapValues { (_, students) -> students.map { it.toGachaStudent() } }
    val pool = gameKeePool ?: runCatching { GameKeeGachaPoolSource.fetchCurrentPools() }.getOrNull().orEmpty()
    val currentEntries = pool.filter { it.server == server.toGameKeeServer() }
      .filter { inPickupWindow(it.character.startAt, it.character.endAt) }
    val pickup = currentEntries.map { it.character.toPickupStudent() }
    return GachaV2Pool(
      server = server,
      common = common,
      pickup = pickup,
      pickupStart = currentEntries.minOfOrNull { it.character.startAt } ?: 0,
      pickupEnd = currentEntries.maxOfOrNull { it.character.endAt } ?: 0,
    )
  }

  /**
   * 抽卡: times=1 单抽, times=10 十连(第10抽保底2星)。
   * @param forceStar 测试钩子: 传入 3 时所有抽数被限制在 3 星档(对应参考实现的 testStar)
   */
  fun draw(
    server: ServerLocale,
    times: Int,
    pool: GachaV2Pool,
    forceStar: Int? = null,
    random: Random = Random.Default,
    pityCount: Int = 0,
  ): List<GachaDrawResult> {
    require(times in 1..10) { "times 必须在 1..10 之间: $times" }
    if (pool.isEmpty) {
      val commonCount = pool.common.values.sumOf { it.size }
      throw IllegalStateException(
        "${server.serverName}抽卡池为空(常驻池${commonCount}人, pickup${pool.pickup.size}人), 请确认 kivo 学生数据与 GameKee 当期卡池可用"
      )
    }
    val results = mutableListOf<GachaDrawResult>()
    // 十连保底: 前9抽全是1星时, 第10抽至少2星
    var must = true
    var runningPity = pityCount
    for (i in 1..times) {
      var rNum = random.nextDouble() * 100
      if (forceStar != null) rNum = rNum % forceStar
      if (i == times && times == 10 && must) rNum = applyGuarantee(rNum)
      // 200 抽保底: 距上次 pickup 累计满 200 抽时, 本次强制 pickup(优先于普通概率)
      val forcedPickup = runningPity + 1 >= PITY_LIMIT && pool.pickup.isNotEmpty()
      val result = when {
        forcedPickup -> pool.pickup[random.nextInt(pool.pickup.size)].toResult(isPickup = true)
        else -> when (decideRoll(rNum, pool.pickup.isNotEmpty())) {
          RollType.EASTER_EGG -> GachaDrawResult(3, EASTER_EGG_NAME, EASTER_EGG_DESC_NAME, EASTER_EGG_DEV_NAME, custom = true)
          RollType.PICKUP_3 -> pool.pickup[random.nextInt(pool.pickup.size)].toResult(isPickup = true)
          RollType.COMMON_3 -> rollCommon(pool, 3, random)
          RollType.COMMON_2 -> rollCommon(pool, 2, random)
          RollType.COMMON_1 -> rollCommon(pool, 1, random)
        }
      }
      // 推进保底计数: 命中 pickup 清零, 否则 +1; 满 200 抽(保底触发)同样清零
      runningPity = if (result.isPickup || runningPity + 1 >= PITY_LIMIT) 0 else runningPity + 1
      if (result.star != 1) must = false
      results += result
    }
    return results
  }

  /** 十连第10抽保底: 限定 rNum 到 [0,21), 保证至少 2 星 */
  internal fun applyGuarantee(rNum: Double): Double = rNum % 21

  /** 根据 [0,100) 的随机数决定落点, 阈值与官方/参考实现一致 */
  internal fun decideRoll(rNum: Double, hasPickup: Boolean): RollType = when {
    rNum <= 0.05 -> RollType.EASTER_EGG
    rNum <= 0.7 && hasPickup -> RollType.PICKUP_3
    rNum <= 3 -> RollType.COMMON_3
    rNum <= 21 -> RollType.COMMON_2
    else -> RollType.COMMON_1
  }

  /** 解析服务器参数: 支持 日服/国服/国际服 及 jp/cn/global 等别名 */
  fun resolveServer(raw: String?): ServerLocale? = when (raw?.trim()?.lowercase()) {
    "日服", "jp", "jpn" -> ServerLocale.JP
    "国服", "cn" -> ServerLocale.CN
    "国际服", "global", "gl", "glb", "en" -> ServerLocale.GLOBAL
    else -> null
  }

  /** 读取用户默认抽卡服务器, 未设置时默认日服 */
  fun getUserServer(userId: Long): ServerLocale {
    val stored = DataBaseProvider.query { GachaUserSetting.findById(userId)?.server }
    return resolveServer(stored) ?: ServerLocale.JP
  }

  /** 保存用户默认抽卡服务器 */
  fun setUserServer(userId: Long, server: ServerLocale) {
    DataBaseProvider.query {
      val record = GachaUserSetting.findById(userId)
      if (record == null) GachaUserSetting.new(userId) { this.server = server.serverName }
      else record.server = server.serverName
    }
  }

  /** 读取用户在某服务器的保底计数(距上次 pickup 的抽数), 无记录或数据库未连接时默认 0 */
  fun getPityCount(userId: Long, server: ServerLocale): Int =
    DataBaseProvider.query {
      GachaPity.find { (GachaPityTable.id eq userId) and (GachaPityTable.server eq server.serverName) }
        .firstOrNull()?.count
    } ?: 0

  /** 保存用户在某服务器的保底计数(数据库未连接时静默跳过) */
  fun updatePityCount(userId: Long, server: ServerLocale, count: Int) {
    DataBaseProvider.query {
      val record = GachaPity.find { (GachaPityTable.id eq userId) and (GachaPityTable.server eq server.serverName) }
        .firstOrNull()
      if (record == null) GachaPity.new(userId) { this.server = server.serverName; this.count = count }
      else record.count = count
    }
  }

  /** 依据抽卡结果推进保底计数: 命中 pickup 清零, 否则每抽 +1; 满 200 抽(保底触发)同样清零 */
  internal fun computePityAfter(pityBefore: Int, results: List<GachaDrawResult>): Int {
    var pity = pityBefore
    for (result in results) {
      if (result.isPickup || pity + 1 >= PITY_LIMIT) pity = 0
      else pity += 1
    }
    return pity
  }

  /** 解析抽卡服务器: 命令参数优先, 未提供时用该用户保存的偏好 */
  fun resolveDrawServer(userId: Long, raw: String?): ServerLocale? {
    if (raw.isNullOrBlank()) return getUserServer(userId)
    return resolveServer(raw)
  }

  /** 一次抽卡的完整结果(含统计与累计点数), 供命令层直接发送 */
  data class DrawReport(
    val server: ServerLocale,
    val times: Int,
    val results: List<GachaDrawResult>,
    val hitPickup: Boolean,
    val star1: Int,
    val star2: Int,
    val star3: Int,
    val points: Int,
    /** 本次抽卡后的保底计数(距上次 pickup 的抽数), 用于结果图右下角展示 */
    val pityCount: Int = 0,
  )

  /**
   * 完整抽卡流程: 校验每日次数 → 建池抽卡 → 写入历史。
   * @return 石头不够(次数耗尽)时返回 null
   */
  fun performDraw(userId: Long, groupId: Long, times: Int, server: ServerLocale, forceStar: Int? = null): DrawReport? {
    // 一次请求拿到三服当期卡池: 供建池/pickup/UP 名称复用, 避免重复请求
    val gameKeePool = runCatching { GameKeeGachaPoolSource.fetchCurrentPools() }.getOrNull().orEmpty()
    val pool = buildPool(server, gameKeePool).takeIf { !it.isEmpty }
      ?: buildFallbackPool(server, gameKeePool.filter { it.server == server.toGameKeeServer() }.map { it.character })
    val allowed = GachaUtil.checkTime(userId, groupId, times)
    if (allowed <= 0) return null
    val actualTimes = minOf(allowed, times)
    val pityBefore = getPityCount(userId, server)
    val results = applyGameKeePickupNames(
      server,
      draw(server, actualTimes, pool, forceStar, pityCount = pityBefore),
      gameKeePool.map { it.character },
    )
    val star1 = results.count { it.star == 1 }
    val star2 = results.count { it.star == 2 }
    val star3 = results.count { it.star == 3 }
    val hit = results.any { it.isPickup }
    val pityAfter = computePityAfter(pityBefore, results)
    updatePityCount(userId, server, pityAfter)
    val history = GachaUtil.getHistory(userId, groupId)
    GachaUtil.updateHistory(userId, groupId, addPoints = actualTimes, addCount3 = star3, dog = hit)
    return DrawReport(server, actualTimes, results, hit, star1, star2, star3, history.points + actualTimes, pityAfter)
  }

  /** 抽卡结果文案: 单抽输出结果行, 十连输出汇总+逐行结果 */
  fun formatReport(report: DrawReport): String = buildString {
    appendLine("(${report.server.serverName}卡池)")
    if (report.times == 1) {
      appendLine(formatResult(report.results.single()))
      append("${report.points} points")
    } else {
      appendLine("————————十连结果————————")
      appendLine("3星:${report.star3} 2星:${report.star2} 1星:${report.star1} ${report.points} points")
      append(formatResults(report.results))
    }
  }
  /** 单条结果文本, 形如 (★★★)UP角色 */
  fun formatResult(result: GachaDrawResult): String =
    "(${starString[result.star]})${result.name}"

  /** 多条结果文本 */
  fun formatResults(results: List<GachaDrawResult>): String =
    results.joinToString("\n") { formatResult(it) }

  /**
   * 当期 pickup 角色名改用 GameKee 当期卡池名称(三服共用一次请求, 每次抽卡时获取)。
   * 匹配规则: 先精确比对中文名, 再尝试包含匹配; 均失败时保留原名称。
   */
  internal fun applyGameKeePickupNames(
    server: ServerLocale,
    results: List<GachaDrawResult>,
    characters: List<GameKeeGachaPoolSource.GachaCharacter>? = null,
  ): List<GachaDrawResult> {
    if (results.none { it.isPickup }) return results
    val pool = characters ?: runCatching { GameKeeGachaPoolSource.fetchPool(server.toGameKeeServer()) }
      .getOrNull().orEmpty()
    if (pool.isEmpty()) return results
    return results.map { result ->
      if (!result.isPickup) return@map result
      val gameKeeName = pool.firstOrNull { it.name == result.name }?.name
        ?: pool.firstOrNull { it.name.contains(result.name) || result.name.contains(it.name) }?.name
        ?: return@map result
      result.copy(name = gameKeeName)
    }
  }

  private fun KivoStudentSource.KivoStudent.toGachaStudent() = GachaStudent(
    id = id,
    name = name,
    descName = descName,
    devName = "",
    star = star,
    avatar = avatar,
  )

  /** 当期卡池时间窗判断: 仅允许抽取已经开始且未结束的卡池; 时间为 0(未知)时放行 */
  private fun inPickupWindow(startAt: Long, endAt: Long): Boolean {
    if (startAt <= 0 || endAt <= 0) return true
    val now = System.currentTimeMillis() / 1000
    return startAt <= now && now <= endAt
  }

  private fun rollCommon(pool: GachaV2Pool, star: Int, random: Random): GachaDrawResult {
    val target = pool.common[star].orEmpty()
    if (target.isNotEmpty()) return target[random.nextInt(target.size)].toResult()
    // 该星级池为空时兜底到其它星级, 避免空池崩溃
    val all = pool.common[3].orEmpty() + pool.common[2].orEmpty() + pool.common[1].orEmpty()
    if (all.isNotEmpty()) return all[random.nextInt(all.size)].toResult()
    // 常驻池为空(兜底模式): 回落到当期 pickup, 保持 3 星
    if (pool.pickup.isNotEmpty()) return pool.pickup[random.nextInt(pool.pickup.size)].toResult(isPickup = true)
    throw IllegalStateException("常驻池为空")
  }

  private fun GachaStudent.toResult(isPickup: Boolean = false) =
    GachaDrawResult(id = id, star = star, name = name, descName = descName, devName = devName, avatar = avatar, isPickup = isPickup)
}
