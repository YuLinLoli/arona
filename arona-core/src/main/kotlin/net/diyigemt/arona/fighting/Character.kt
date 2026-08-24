/**
 * 文件说明：本文件属于 战斗模拟、角色属性和地形规则。
 * 具体职责：围绕 Character 提供对应的实现、数据结构或测试。
 */
package net.diyigemt.arona.fighting

// 中文说明：定义 Character 类型，用于封装本模块的数据或处理行为。
open class Character(
  open val name: String = "",
  open val level: Int = 0,
  open var health: Int = 0,
  open val attack: Int = 0,
  open val defence: Int = 0,
  open val healing: Int = 0,
  open val hitValue: Int = 0,
  open val dodgeValue: Int = 0,
  open val criticalValue: Int = 0,
  open val criticalAgainstValue: Int = 0,
  open val stableValue: Int = 0,
  open val distance: Int = 0,
  open val CC: Int = 0,
  open val CCAgainst: Int = 0,
  open val costGenerate: Int = 0,
  open val terrainSatisfies: List<TerrainSatisfyEnum> = listOf(TerrainSatisfyEnum.D, TerrainSatisfyEnum.D, TerrainSatisfyEnum.D),
  open val weapon: Weapon,
  open val position: Position,
  var manager: FightManager? = null
) {
  val maxHealth: Int
  init {
    weapon.onwer = this
    maxHealth = health
  }
  fun tick(enemies: List<Character>, friends: List<Character>): Unit {
    if (health <= 0) return
    this.weapon.tick(enemies)
  }
}
