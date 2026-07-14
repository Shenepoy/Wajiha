package com.wajiha.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class SystemNotificationKind {
    Info,
    Success,
    Warning,
    Error,
}

data class SystemNotification(
    val id: Long,
    val title: String,
    val body: String,
    val kind: SystemNotificationKind = SystemNotificationKind.Info,
    val createdAtMs: Long = 0L,
    val read: Boolean = false,
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

    /** Increments on each [post] so the status pill can peek when tucked. */
    private val _peekRequestId = MutableStateFlow(0L)
    val peekRequestId: StateFlow<Long> = _peekRequestId.asStateFlow()

    private var nextId = 1L
    private var lastToggleAtMs: Long = 0L

    @OptIn(ExperimentalTime::class)
    fun post(
        title: String,
        body: String,
        kind: SystemNotificationKind = SystemNotificationKind.Info,
    ) {
        val item =
            SystemNotification(
                id = nextId++,
                title = title,
                body = body,
                kind = kind,
                createdAtMs = Clock.System.now().toEpochMilliseconds(),
            )
        _notifications.update { listOf(item) + it.take(MAX_ITEMS - 1) }
        _peekRequestId.update { it + 1 }
        // Panel already open: don't leave an unread badge while the list is visible.
        if (_panelOpen.value) markAllRead()
    }

    /**
     * Toggle the notification panel. Debounced so Thor's digital BUTTON_R2 and
     * analog AXIS_GAS for one physical press cannot open-then-immediately-close.
     *
     * @return true when the toggle was applied; false when absorbed by debounce.
     */
    @OptIn(ExperimentalTime::class)
    fun togglePanel(): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastToggleAtMs < TOGGLE_DEBOUNCE_MS) return false
        lastToggleAtMs = now
        _panelOpen.update { open ->
            val next = !open
            if (next) markAllRead()
            next
        }
        return true
    }

    fun openPanel() {
        if (_panelOpen.value) return
        markAllRead()
        _panelOpen.value = true
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
        private const val TOGGLE_DEBOUNCE_MS = 280L
    }
}
