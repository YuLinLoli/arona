/**
 * 文件说明：本文件属于 数据库表结构、实体和数据访问逻辑。
 * 具体职责：围绕 BaseDataBase 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.db

import net.diyigemt.arona.command.cache.GachaCache
import net.diyigemt.arona.db.DataBaseProvider.query
import net.diyigemt.arona.db.announcement.RemoteActionTable
import net.diyigemt.arona.db.gacha.*
import net.diyigemt.arona.db.image.ImageTable
import net.diyigemt.arona.db.name.GameNameTable
import net.diyigemt.arona.db.name.TeacherNameTable
import net.diyigemt.arona.db.system.SystemTable
import net.diyigemt.arona.db.tarot.TarotRecordTable
import net.diyigemt.arona.db.tarot.TarotTable
import org.jetbrains.exposed.sql.SchemaUtils

// 中文说明：定义 BaseDataBase 对象，集中提供本文件的共享功能。
object BaseDataBase {

  fun init() {
    query {
      SchemaUtils.create(
        GachaCharacterTable,
        GachaPoolTable,
        GachaPoolCharacterTable,
        GachaHistoryTable,
        GachaLimitTable,
        TarotTable,
        TarotRecordTable,
        TeacherNameTable,
        GameNameTable,
        ImageTable,
        SystemTable,
        RemoteActionTable
      )
    }
    GachaCache.init()
  }

}
