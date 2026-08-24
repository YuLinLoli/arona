/**
 * 文件说明：本文件属于 战斗模拟、角色属性和地形规则。
 * 具体职责：围绕 TerrainSatisfyEnum 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.fighting

// 中文说明：定义 TerrainSatisfyEnum 类型，用于封装本模块的数据或处理行为。
enum class TerrainSatisfyEnum(
  val rate: Float,
  val coverBlock: Float
) {
  D(0.8F, 0F),
  C(0.9F, 0.15F),
  B(1.0F, 0.30F),
  A(1.1F, 0.45F),
  S(1.2F, 0.60F),
  SS(1.3F, 0.75F)
}
