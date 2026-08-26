package net.diyigemt.arona.runtime

object RuntimeGachaConfig {
  @Volatile var star1Rate: Float = 79f
  @Volatile var star2Rate: Float = 18.5f
  @Volatile var star3Rate: Float = 2.5f
  @Volatile var star2PickupRate: Float = 3f
  @Volatile var star3PickupRate: Float = 0.7f
  @Volatile var activePool: Int = 1
  @Volatile var revokeTime: Int = 10
  @Volatile var limit: Int = 0
  @Volatile var day: Int = 0
  @Volatile var maxDot: Int = 10

  fun recalcMaxDot() {
    maxDot = listOf(star1Rate, star2Rate, star3Rate, star2PickupRate, star3PickupRate)
      .map { rate -> dotPosition(rate.toString()) }
      .maxOf { it }
  }

  fun syncFromPlugin() {
    val config = net.diyigemt.arona.config.AronaGachaConfig
    star1Rate = config.star1Rate
    star2Rate = config.star2Rate
    star3Rate = config.star3Rate
    star2PickupRate = config.star2PickupRate
    star3PickupRate = config.star3PickupRate
    activePool = config.activePool
    revokeTime = config.revokeTime
    limit = config.limit
    day = config.day
    maxDot = config.maxDot
  }

  private fun dotPosition(s: String): Int =
    if (s.indexOf(".") == -1) 1 else s.length - s.indexOf(".") - 1
}
