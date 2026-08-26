package com.smartworkalarm.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("변경사항을 저장할까요?") },
        text = { Text("저장하지 않은 변경사항이 있습니다.") },
        confirmButton = {
            TextButton(onClick = onSave) { Text("저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("저장 안 함") }
                TextButton(onClick = onCancel) { Text("취소") }
            }
        },
    )
}

@Composable
fun UnsavedChangesBackHandler(
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    BackHandler {
        if (hasUnsavedChanges) showDialog = true else onBack()
    }
    if (showDialog) {
        UnsavedChangesDialog(
            onSave = {
                showDialog = false
                onSave()
            },
            onDiscard = {
                showDialog = false
                onDiscard()
            },
            onCancel = { showDialog = false },
        )
    }
}
