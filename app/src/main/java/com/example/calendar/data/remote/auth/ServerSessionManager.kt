package com.example.calendar.data.remote.auth

/** Local Room ids and remote user ids are distinct namespaces. Tokens never leave memory. */
class ServerSessionManager(private val tokenLifetimeMillis: Long = 24 * 60 * 60 * 1000L) {
    private data class Active(val ownerId: Long, val serverUserId: Long, val token: String, val expiresAt: Long)
    private val _owner = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val owner: kotlinx.coroutines.flow.StateFlow<Long?> = _owner
    @Volatile private var active: Active? = null
    private var generation = 0L
    @Synchronized fun generation(): Long = generation
    @Synchronized fun install(ownerId: Long, token: String, serverUserId: Long = ownerId,
        expiresAtMillis: Long = System.currentTimeMillis() + tokenLifetimeMillis,
        expectedGeneration: Long = generation) {
        check(expectedGeneration == generation) { "Server authentication was cancelled by an account or endpoint change" }
        require(ownerId > 0 && serverUserId > 0 && token.isNotBlank() && expiresAtMillis > System.currentTimeMillis())
        active = Active(ownerId, serverUserId, token, expiresAtMillis)
        _owner.value = ownerId
    }
    @Synchronized private fun current(): Active? {
        if (active?.expiresAt?.let { it <= System.currentTimeMillis() } == true) clear()
        return active
    }
    @Synchronized fun tokenFor(ownerId: Long): String? = current()?.takeIf { it.ownerId == ownerId }?.token
    @Synchronized fun serverUserIdFor(ownerId: Long): Long? = current()?.takeIf { it.ownerId == ownerId }?.serverUserId
    @Synchronized fun clear() { active = null; _owner.value = null; generation++ }
    @Synchronized fun ownerIdOrNull(): Long? = current()?.ownerId
}
