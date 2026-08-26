package net.diyigemt.arona.runtime

import java.nio.file.Path

object RuntimeServices {
  @Volatile
  var isStandalone: Boolean = false

  @Volatile
  var messageSender: MessageSender? = null

  @Volatile
  var dataRoot: Path? = null
}
