package net.diyigemt.arona.onebot

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 正向连接重试策略：每 [reconnectInterval] 毫秒重试一次，
 * 连续 [maxFailures] 次失败后休息 [restMillis] 毫秒再重试，并在黑窗口提示。
 */
class OneBotRetryPolicy(
  private val reconnectInterval: Long,
  private val maxFailures: Int = 5,
  private val restMillis: Long = 30_000,
) {
  private var failCount = 0
  private var scheduled = false

  fun onSuccess() {
    failCount = 0
  }

  fun onFailure(scheduler: ScheduledExecutorService, label: String, action: () -> Unit) {
    if (scheduled) return
    failCount++
    val shouldRest = failCount % maxFailures == 0
    val delay = if (shouldRest) restMillis else reconnectInterval.coerceAtLeast(1000)
    if (shouldRest) {
      println("[OneBot $label] 连续 $maxFailures 次连接失败，休息 ${restMillis / 1000} 秒后重试")
    } else {
      println("[OneBot $label] 连接失败（第 $failCount 次），${delay / 1000} 秒后重试")
    }
    scheduled = true
    scheduler.schedule({
      scheduled = false
      try {
        action()
      } catch (error: Throwable) {
        println("[OneBot $label] 重试失败: ${error.message}")
        onFailure(scheduler, label, action)
      }
    }, delay, TimeUnit.MILLISECONDS)
  }

  fun reset() {
    failCount = 0
    scheduled = false
  }
}
