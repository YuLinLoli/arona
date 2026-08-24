/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 Raid 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 *@Author hjn
 *@Create 2022/8/28
 */
object Raid : Table("Raid") {
  val Id : Column<Int> = integer("Id")
  val IsReleased : Column<Boolean> = bool("IsReleased")
  val NameCn : Column<String> = varchar("NameCn", 20).default("peroro-sama")
  val CurrentJPN : Column<String> = varchar("CurrentJPN", 20)
  val CurrentGLB : Column<String> = varchar("CurrentGLB", 20)
}
