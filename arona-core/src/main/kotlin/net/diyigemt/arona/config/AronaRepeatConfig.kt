/**
 * 文件说明：本文件属于 插件配置项定义与默认配置。
 * 具体职责：围绕 AronaRepeatConfig 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object AronaRepeatConfig: AutoSavePluginConfig("arona-repeat") {

  @ValueDescription("复读次数")
  val times: Int by value(3)

}
