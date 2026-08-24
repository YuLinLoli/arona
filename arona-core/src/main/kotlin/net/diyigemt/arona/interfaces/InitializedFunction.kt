/**
 * 文件说明：本文件属于 跨模块复用的接口和生命周期约定。
 * 具体职责：围绕 InitializedFunction 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.interfaces

// 中文说明：定义 InitializedFunction 类型，用于封装本模块的数据或处理行为。
abstract class InitializedFunction {

  open val priority = 0
  abstract fun init()

}
