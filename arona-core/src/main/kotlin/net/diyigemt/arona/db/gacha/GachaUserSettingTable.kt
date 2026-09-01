/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 GachaUserSettingTable 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db.gacha

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column

// 中文说明：定义 GachaUserSettingTable 类型，用于封装本模块的数据或处理行为。
object GachaUserSettingTable: IdTable<Long>(name = "GachaUserSetting") {
  override val id: Column<EntityID<Long>> = long("qq").entityId()
  /** 用户默认抽卡服务器: 日服/国服/国际服 */
  val server: Column<String> = char("server", 10)

  override val primaryKey: PrimaryKey = PrimaryKey(id)
}

// 中文说明：定义 GachaUserSetting 类型，用于封装本模块的数据或处理行为。
class GachaUserSetting(id: EntityID<Long>): LongEntity(id) {
  companion object: LongEntityClass<GachaUserSetting>(GachaUserSettingTable)
  var server by GachaUserSettingTable.server
}