/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 GameKeeDAO 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity

/**
 *@Author hjn
 *@Create 2022/7/22
 */
// 中文说明：定义 GameKeeDAO 类型，用于封装本模块的数据或处理行为。
data class GameKeeDAO(
  val code : Int,
  val msg : String,
  val data : List<Data>,
  val meta : Meta
)

// 中文说明：定义 Data 类型，用于封装本模块的数据或处理行为。
data class Data(
  val id : Int,
  var title : String,
  val link_url : String,
  val picture : String,
  val description : String,
  val begin_at : Long,
  val end_at : Long,
  val importance : Int,
  val count_down : Int,
  val pub_area : String
)

// 中文说明：定义 Meta 类型，用于封装本模块的数据或处理行为。
data class Meta(
  val request_id : String,
  val trace_id : String
)
