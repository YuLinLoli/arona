/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 TrainerOverride 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity

/**
 * 保存trainer指令的覆盖信息
 * @author diyigemt
 * @since 1.0.9
 */
@kotlinx.serialization.Serializable
// 中文说明：定义 TrainerOverride 类型，用于封装本模块的数据或处理行为。
data class TrainerOverride(
  val type: OverrideType,
  val name: String,
  val value: String
) {
  enum class OverrideType() {
    IMAGE, // 发送图片
    RAW, // 原生别名
    CODE // mirai-code
  }
}
