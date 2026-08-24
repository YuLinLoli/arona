/**
 * 文件说明：本文件属于 插件配置项定义与默认配置。
 * 具体职责：围绕 AronaTarotConfig 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.config

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object AronaTarotConfig: AutoSavePluginConfig("arona-tarot") {

  @ValueDescription("塔罗牌每天是否只能抽一张(多次抽取结果相同)")
  val dayOne: Boolean by value(false)

  @ValueDescription("顺带发送P站Shi0n老师画的塔罗牌图片")
  val image: Boolean by value(true)

}
