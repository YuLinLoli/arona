/**
 * 文件说明：本文件属于 远程服务调用与服务管理。
 * 具体职责：围绕 RemoteService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.remote

import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
// 中文说明：定义 RemoteService 接口，约定相关实现需要提供的能力。
interface RemoteService<T> {

  val kType: KType
  val type: RemoteServiceAction
  fun handleService(data: T, time: String, aid: Long)
  fun init() {
    RemoteServiceManager.registerService(this.type, this as RemoteService<Any>)
  }

}

// 中文说明：定义 RemoteServiceAction 类型，用于封装本模块的数据或处理行为。
enum class RemoteServiceAction(
  val action: String
) {
  ANNOUNCEMENT("announcement"),
  POOL_UPDATE("poolUpdate"),
  NULL("null");
  companion object {
    fun getRemoteServiceActionByName(name: String): RemoteServiceAction =
      RemoteServiceAction.values()
        .firstOrNull { it.action == name }
        ?: NULL
  }
}
