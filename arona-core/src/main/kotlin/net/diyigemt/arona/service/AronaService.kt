/**
 * 文件说明：本文件属于 Arona 核心业务服务与服务生命周期。
 * 具体职责：围绕 AronaService 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.service

import net.diyigemt.arona.util.sys.SysStatic

// 中文说明：定义 AronaService 接口，约定相关实现需要提供的能力。
interface AronaService {
  val id: Int
  val name: String
  var enable: Boolean

  // remind to registerService
  fun init()

  fun registerService() {
    AronaServiceManager.register(this)
  }
  fun enableService() {}
  fun disableService() {}
}
