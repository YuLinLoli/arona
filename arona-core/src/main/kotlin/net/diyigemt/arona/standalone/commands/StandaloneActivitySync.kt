/**
 * 文件说明：独立模式活动日历同步。
 * 具体职责：将国服/国际服/日服的活动日历写入数据库, 并支持从数据库读取缓存。
 */
package net.diyigemt.arona.standalone.commands

import net.diyigemt.arona.db.DataBaseProvider
import net.diyigemt.arona.db.activity.ActivityCalendarItem
import net.diyigemt.arona.db.activity.ActivityCalendarTable
import net.diyigemt.arona.entity.Activity
import net.diyigemt.arona.entity.ActivityType
import net.diyigemt.arona.entity.ServerLocale
import net.diyigemt.arona.runtime.RuntimeLog
import net.diyigemt.arona.util.ActivityUtil
import net.diyigemt.arona.util.TimeUtil
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.Calendar
import java.util.Date

object StandaloneActivitySync {
  /** 数据库缓存有效期, 超过该时间需要重新联网拉取 */
  private const val CACHE_VALID_MILLS = 12L * 60 * 60 * 1000

  /** 同步全部服务器的活动日历到数据库 */
  fun syncAll() {
    ServerLocale.values().forEach { server ->
      runCatching {
        val activities = when (server) {
          ServerLocale.JP -> ActivityUtil.fetchJPActivity()
          ServerLocale.GLOBAL -> ActivityUtil.fetchENActivity()
          ServerLocale.CN -> ActivityUtil.fetchCNActivity()
        }
        saveToDb(server, activities)
        RuntimeLog.info("${server.serverName}活动日历已更新到数据库")
      }.onFailure {
        RuntimeLog.warning("同步${server.serverName}活动日历失败: ${it.message}")
      }
    }
  }

  /** 将活动写入数据库, 覆盖该服务器旧数据 */
  fun saveToDb(server: ServerLocale, activities: Pair<List<Activity>, List<Activity>>) {
    DataBaseProvider.query {
      ActivityCalendarTable.deleteWhere { ActivityCalendarTable.server eq server.dbName }
      val now = System.currentTimeMillis()
      (activities.first + activities.second).forEach { activity ->
        if (activity.startTime <= 0 || activity.endTime <= 0) return@forEach
        ActivityCalendarTable.insert {
          it[ActivityCalendarTable.server] = server.dbName
          it[content] = activity.content
          it[type] = activity.type.name
          it[start] = activity.startTime
          it[end] = activity.endTime
          it[updatedAt] = now
        }
      }
    }
  }

  /** 从数据库读取活动日历; 缓存过期或不存在时返回 null */
  fun loadFromDb(server: ServerLocale): Pair<List<Activity>, List<Activity>>? {
    val rows = DataBaseProvider.query {
      ActivityCalendarItem.find { ActivityCalendarTable.server eq server.dbName }.toList()
    } ?: return null
    if (rows.isEmpty()) return null
    val latest = rows.maxOf { it.updatedAt }
    if (System.currentTimeMillis() - latest > CACHE_VALID_MILLS) return null
    val now = Calendar.getInstance().time
    val active = mutableListOf<Activity>()
    val pending = mutableListOf<Activity>()
    rows.forEach { row ->
      val start = Date(row.start)
      val end = Date(row.end)
      val activity = Activity(
        content = row.content,
        time = "",
        type = runCatching { ActivityType.valueOf(row.type) }.getOrDefault(ActivityType.NULL),
        serverLocale = server,
        startTime = row.start,
        endTime = row.end,
      )
      if (now.before(start)) {
        activity.time = TimeUtil.calcTime(start, true)
        pending.add(activity)
      } else if (now.before(end)) {
        activity.time = TimeUtil.calcTime(end, false)
        active.add(activity)
      }
    }
    return active to pending
  }
}
