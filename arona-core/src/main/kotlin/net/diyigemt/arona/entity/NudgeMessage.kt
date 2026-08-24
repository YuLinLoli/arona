/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 NudgeMessage 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity

@kotlinx.serialization.Serializable
// 中文说明：定义 NudgeMessage 类型，用于封装本模块的数据或处理行为。
data class NudgeMessage(
  val message: String,
  val weight: Int = 1
)
