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
  var code : Int,
  var msg : String,
  var data : List<Data>,
  var meta : Meta
)

// 中文说明：定义 Data 类型，用于封装本模块的数据或处理行为。
data class Data(
  var id : Int,
  var title : String,
  var link_url : String,
  var picture : String,
  var description : String,
  var begin_at : Long,
  var end_at : Long,
  var importance : Int,
  var count_down : Int,
  var pub_area : String
)

// 中文说明：定义 Meta 类型，用于封装本模块的数据或处理行为。
data class Meta(
  var request_id : String,
  var trace_id : String
)

data class GameKeeEntryResponse(
  var code: Int = 0,
  var msg: String = "",
  var data: GameKeeEntry? = null
)

data class GameKeeEntry(
  var id: Int = 0,
  var name: String = "",
  var pid: Int = 0,
  var content_id: Int? = null,
  var child: List<GameKeeEntry>? = null
)
data class GameKeeContentResponse(
  var code: Int = 0,
  var msg: String = "",
  var data: GameKeeContent? = null
)

data class GameKeeContent(
  var thumb_list: List<String> = emptyList(),
  var version: String = ""
)
