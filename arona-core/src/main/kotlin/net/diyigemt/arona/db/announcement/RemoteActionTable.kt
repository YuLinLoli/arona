/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 RemoteActionTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.announcement

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column

/**
 * 保存已发送的公告id
 */
object RemoteActionTable: LongIdTable(name = "RemoteAction") {
  val aid: Column<Long> = long("aid")
}

// 中文说明：定义 RemoteActionModel 类型，用于封装本模块的数据或处理行为。
class RemoteActionModel(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<RemoteActionModel>(RemoteActionTable)
  var aid by RemoteActionTable.aid
}
