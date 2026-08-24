/**
 * 文件说明：本文件属于 插件配置项定义与默认配置。
 * 具体职责：围绕 AronaServiceConfig 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object AronaServiceConfig: AutoSavePluginConfig("arona-service") {

  @ValueDescription("配置各功能模块的开关")
  val config: MutableMap<String, Boolean> by value()

}
