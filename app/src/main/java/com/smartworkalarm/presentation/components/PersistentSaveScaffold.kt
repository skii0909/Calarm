package com.smartworkalarm.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState

@Composable
fun PersistentSaveScaffold(
    saveLabel: String,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = PersistentSaveScaffold(
    saveLabel = saveLabel,
    onSave = onSave,
    scrollState = rememberScrollState(),
    content = content,
)

@Composable
internal fun PersistentSaveScaffold(
    saveLabel: String,
    onSave: () -> Unit,
    scrollState: ScrollState,
    bottomBarInsets: WindowInsets = WindowInsets.navigationBars,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(bottomBarInsets.union(safeDrawingInsets))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("persistent-save-action"),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(saveLabel)
                    }
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(scrollState)
                .testTag("persistent-save-content")
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
