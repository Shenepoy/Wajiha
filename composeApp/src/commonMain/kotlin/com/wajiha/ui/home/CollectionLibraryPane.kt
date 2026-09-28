package com.wajiha.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.input.requestContentFocus
import com.wajiha.ui.components.WajihaCardDialog
import com.wajiha.ui.components.WajihaDialog
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadSafeTextField
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.theme.WajihaSpacing

@Composable
fun CollectionLibraryPane(
    collections: List<CollectionSummary>,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val pending = collections.firstOrNull { it.id == pendingDelete }

    if (collections.isEmpty() && !creating) {
        WajihaEmptyState(
            title = "No collections",
            subtitle = "Group games under a name. ROM files stay where they are.",
            modifier = modifier,
            action = {
                GamepadButton(
                    text = "Create",
                    onClick = { creating = true },
                    modifier = Modifier.padding(top = WajihaSpacing.sm),
                )
            },
        )
    } else {
        Column(
            modifier =
                modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WajihaSpacing.sm),
        ) {
            collections.forEach { collection ->
                GamepadSettingRow(
                    label = collection.name,
                    description = collectionGameCountLabel(collection.gameCount),
                    type = SettingType.Action,
                    onActivate = { onSelect(collection.id) },
                    isAtDefault = true,
                )
                WajihaActionSetting(
                    label = "Delete ${collection.name}",
                    description = "Remove this collection. Games stay in the library.",
                    actionLabel = "Delete",
                    onClick = { pendingDelete = collection.id },
                )
            }
            if (creating) {
                CollectionNameFields(
                    onCreate = { name ->
                        onCreate(name)
                        creating = false
                    },
                    onCancel = { creating = false },
                )
            } else {
                WajihaActionSetting(
                    label = "New collection",
                    actionLabel = "Create",
                    onClick = { creating = true },
                )
            }
        }
    }

    WajihaDialog(
        visible = pending != null,
        title = "Delete collection?",
        message =
            if (pending == null) {
                ""
            } else {
                "\"${pending.name}\" will be removed. Games stay in the library."
            },
        onDismiss = { pendingDelete = null },
        onConfirm = {
            pending?.let { onDelete(it.id) }
            pendingDelete = null
        },
        confirmText = "Delete",
        dismissText = "Cancel",
    )
}

@Composable
fun CollectionMembershipDialog(
    visible: Boolean,
    collections: List<CollectionSummary>,
    memberIds: Set<Long>,
    onToggle: (collectionId: Long, member: Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val trimmed = draft.trim()
    LaunchedEffect(visible, creating) {
        if (!visible || !creating) return@LaunchedEffect
        withFrameNanos { }
        nameFocus.requestContentFocus()
    }
    WajihaCardDialog(
        visible = visible,
        title = "Add to collection",
        onDismiss = {
            if (creating) {
                creating = false
                draft = ""
            } else {
                onDismiss()
            }
        },
        dismissText = if (creating) "Cancel" else "Done",
        confirmText = "Create",
        confirmEnabled = !creating || trimmed.isNotEmpty(),
        onConfirm = {
            if (!creating) {
                creating = true
            } else if (trimmed.isNotEmpty()) {
                onCreate(trimmed)
                draft = ""
                creating = false
            }
        },
    ) {
        if (collections.isEmpty() && !creating) {
            Text(
                text = "No collections yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        collections.forEach { collection ->
            val member = collection.id in memberIds
            GamepadSettingRow(
                label = collection.name,
                description = collectionGameCountLabel(collection.gameCount),
                type = SettingType.Toggle,
                checked = member,
                onCheckedChange = { checked -> onToggle(collection.id, checked) },
                isAtDefault = true,
            )
        }
        if (creating) {
            GamepadSafeTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "Collection name",
                modifier = Modifier.fillMaxWidth(),
                focusRequester = nameFocus,
            )
        }
    }
}

@Composable
private fun CollectionNameFields(
    onCreate: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    GamepadSafeTextField(
        value = name,
        onValueChange = { name = it },
        label = "Collection name",
        modifier = Modifier.fillMaxWidth(),
    )
    WajihaActionSetting(
        label = "Create collection",
        actionLabel = "Create",
        onClick = {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) onCreate(trimmed)
        },
    )
    WajihaActionSetting(
        label = "Cancel",
        actionLabel = "Cancel",
        onClick = onCancel,
    )
}
