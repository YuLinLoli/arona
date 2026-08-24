/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 CurrentData 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.data.schaledb

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

/**
 *@Author hjn
 *@Create 2022/8/28
 */

object CurrentData : Table("CurrentData") {
  val value : Column<Int> = integer("value")
  val type : Column<String> = varchar("type", 10)
  val server : Column<String> = varchar("server", 10)
  val start : Column<Long> = long("start")
  val end : Column<Long> = long("end")
}
