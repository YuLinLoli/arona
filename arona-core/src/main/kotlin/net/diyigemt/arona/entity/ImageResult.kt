/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 ImageResult 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity

import java.io.File

@kotlinx.serialization.Serializable
// 中文说明：定义 ImageResult 类型，用于封装本模块的数据或处理行为。
data class ImageResult(
  val id: Int = 0,
  val name: String = "",
  val path: String = "",
  val hash: String = "",
  val type: Int = 1,
)

// 中文说明：定义 ImageRequestResult 类型，用于封装本模块的数据或处理行为。
data class ImageRequestResult(
  val list: List<ImageResult> = listOf(),
  val file: File? = null
)

const val FuzzyImageResult = 0
