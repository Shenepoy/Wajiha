package com.wajiha.state

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SystemNotificationKind {
    Info,
    Success,
    Warning,
    Error
}

data class SystemNotification(
    val id: Long,
    val title: String,
    val body: String,
    val kind: SystemNotificationKind = SystemNotificationKind.Info,
    val createdAtMs: Long = 0L,
    val read: Boolean = false
)

/**
 * In-app system notification center shown on the top-screen status bar.
 * Workers and UI post here; R2 toggles the panel.
 */
class SystemNotificationStore {

    private val _notifications = MutableStateFlow<List<SystemNotification>>(emptyList())
    val notifications: StateFlow<List<SystemNotification>> = _notifications.asStateFlow()

    private val _panelOpen = MutableStateFlow(false)
    val panelOpen: StateFlow<Boolean> = _panelOpen.asStateFlow()

    private var nextId = 1L

    @OptIn(ExperimentalTime::class)
    fun post(
        title: String,
        body: String,
        kind: SystemNotificationKind = SystemNotificationKind.Info
    ) {
        val item = SystemNotification(
            id = nextId++,
            title = title,
            body = body,
            kind = kind,
            createdAtMs = Clock.System.now().toEpochMilliseconds()
        )
        _notifications.update { listOf(item) + it.take(MAX_ITEMS - 1) }
    }

    fun togglePanel() {
        _panelOpen.update { open ->
            val next = !open
            if (next) markAllRead()
            next
        }
    }

    fun closePanel() {
        _panelOpen.value = false
    }

    fun dismiss(id: Long) {
        _notifications.update { list -> list.filterNot { it.id == id } }
    }

    fun clearAll() {
        _notifications.value = emptyList()
        _panelOpen.value = false
    }

    private fun markAllRead() {
        _notifications.update { list -> list.map { it.copy(read = true) } }
    }

    companion object {
        private const val MAX_ITEMS = 40
    }
}
