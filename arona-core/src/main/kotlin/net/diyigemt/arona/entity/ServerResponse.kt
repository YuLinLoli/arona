/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 ServerResponse 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity

import kotlinx.serialization.Serializable

@Serializable
// 中文说明：定义 ServerResponse 类型，用于封装本模块的数据或处理行为。
data class ServerResponse<T>(
  val status: Int,
  val message: String,
  val data: T
)
