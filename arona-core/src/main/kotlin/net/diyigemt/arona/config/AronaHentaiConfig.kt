/**
 * 文件说明：本文件属于 插件配置项定义与默认配置。
 * 具体职责：围绕 AronaHentaiConfig 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.config

import net.diyigemt.arona.entity.NudgeMessage
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.event.EventPriority

object AronaHentaiConfig: AutoSavePluginConfig("arona-hentai") {

  @ValueDescription("要被骂的id")
  var listen: MutableList<Long> by value()
  @ValueDescription("返回被骂的信息")
  var messageList: MutableList<NudgeMessage> by value()

}
