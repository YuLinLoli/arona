/**
 * 文件说明：活动日历数据库表。
 * 具体职责：缓存国服/国际服/日服的活动日历, 供独立模式离线查询。
 */
package net.diyigemt.arona.db.activity

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column

object ActivityCalendarTable : IntIdTable(name = "ActivityCalendar") {
  val server: Column<String> = varchar("server", 10)
  val content: Column<String> = varchar("content", 255)
  val type: Column<String> = varchar("type", 30)
  val start: Column<Long> = long("start")
  val end: Column<Long> = long("end")
  val updatedAt: Column<Long> = long("updated_at")
}

class ActivityCalendarItem(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<ActivityCalendarItem>(ActivityCalendarTable)

  var server by ActivityCalendarTable.server
  var content by ActivityCalendarTable.content
  var type by ActivityCalendarTable.type
  var start by ActivityCalendarTable.start
  var end by ActivityCalendarTable.end
  var updatedAt by ActivityCalendarTable.updatedAt
}
