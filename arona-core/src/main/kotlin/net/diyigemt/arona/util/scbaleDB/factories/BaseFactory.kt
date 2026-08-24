/**
 * 文件说明：本文件属于 通用工具、网络、图片和业务辅助函数。
 * 具体职责：围绕 BaseFactory 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.util.scbaleDB.factories

/**
 *@Author hjn
 *@Create 2022/8/20
 */
// 中文说明：定义 BaseFactory 类型，用于封装本模块的数据或处理行为。
abstract class BaseFactory {
  fun getValueById(id : Int) : String?{
    for (member in this::class.members){
      if (member.name == id.toString()) return member.call(this).toString()
    }

    return null
  }
}
