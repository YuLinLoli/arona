/**
 * 文件说明：本文件属于 业务数据实体与接口响应模型。
 * 具体职责：围绕 BaseDAO 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.entity.schaleDB

/**
 *@Author hjn
 *@Create 2022/8/29
 */
// 中文说明：定义 BaseDAO 接口，约定相关实现需要提供的能力。
interface BaseDAO {
  fun sendToDataBase()

  fun <T : BaseDAO> toModel(dao : T) : T

  fun getServer(type : Boolean) : String = if(type) "JPN" else "GLB"
}
