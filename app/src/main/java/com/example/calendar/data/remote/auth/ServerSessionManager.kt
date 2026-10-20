package com.example.calendar.data.remote.auth

/** Access tokens stay in memory and are always tied to the local account that obtained them. */
class ServerSessionManager(private val tokenLifetimeMillis: Long = 24 * 60 * 60 * 1000L) {
    private data class Active(val ownerId: Long, val token: String, val expiresAt: Long)
    @Volatile private var active: Active? = null

    @Synchronized fun install(ownerId: Long, token: String) {
        require(ownerId > 0 && token.isNotBlank())
        active = Active(ownerId, token, System.currentTimeMillis() + tokenLifetimeMillis)
    }

    @Synchronized fun tokenFor(ownerId: Long): String? {
        val session = active ?: return null
        if (session.expiresAt <= System.currentTimeMillis()) { active = null; return null }
        return session.token.takeIf { session.ownerId == ownerId }
    }

    @Synchronized fun clear() { active = null }
    @Synchronized fun ownerIdOrNull(): Long? {
        val session = active ?: return null
        if (session.expiresAt <= System.currentTimeMillis()) { active = null; return null }
        return session.ownerId
    }
}
