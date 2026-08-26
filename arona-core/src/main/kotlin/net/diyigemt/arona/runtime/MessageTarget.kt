package net.diyigemt.arona.runtime

sealed interface MessageTarget {
  val id: Long

  data class Group(override val id: Long) : MessageTarget
  data class Private(override val id: Long) : MessageTarget
}
