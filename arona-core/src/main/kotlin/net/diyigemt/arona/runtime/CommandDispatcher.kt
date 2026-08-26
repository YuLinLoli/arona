package net.diyigemt.arona.runtime

fun interface CommandDispatcher {
  suspend fun dispatch(context: CommandContext): Boolean
}

fun interface CommandHandler {
  suspend fun handle(context: CommandContext, arguments: List<String>)
}

data class CommandRegistration(
  val names: Set<String>,
  val description: String,
  val handler: CommandHandler,
)

class SimpleCommandDispatcher(
  registrations: List<CommandRegistration>,
  private val fallbackHandler: (suspend (CommandContext) -> Unit)? = null,
) : CommandDispatcher {
  private val commands = registrations.flatMap { registration ->
    registration.names.map { name -> normalize(name) to registration }
  }.toMap()

  override suspend fun dispatch(context: CommandContext): Boolean {
    val parts = context.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (parts.isEmpty()) return false
    val registration = commands[normalize(parts.first())] ?: run {
      fallbackHandler?.invoke(context)
      return false
    }
    registration.handler.handle(context, parts.drop(1))
    return true
  }

  private fun normalize(command: String): String = command.trim().lowercase()
}
