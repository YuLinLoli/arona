package net.diyigemt.arona.onebot

import com.google.gson.JsonObject
import java.util.concurrent.CompletableFuture

interface OneBotConnection {
  fun start()
  fun stop()
  fun send(action: OneBotAction): CompletableFuture<OneBotActionResponse?>
  fun broadcast(event: JsonObject)
}

interface OneBotEventHandler {
  fun onEvent(event: OneBotEvent, connection: OneBotConnection)
}

class LoggingEventHandler : OneBotEventHandler {
  override fun onEvent(event: OneBotEvent, connection: OneBotConnection) {
    println("[OneBot event] post_type=${event.postType}, message_type=${event.messageType}, user_id=${event.userId}, group_id=${event.groupId}, text=${OneBotProtocol.extractText(event)}")
  }
}
