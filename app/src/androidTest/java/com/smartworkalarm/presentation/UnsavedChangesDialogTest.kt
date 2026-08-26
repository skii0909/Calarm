package com.smartworkalarm.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.smartworkalarm.presentation.components.UnsavedChangesBackHandler
import com.smartworkalarm.presentation.components.UnsavedChangesDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UnsavedChangesDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dialogShowsExactActionsAndDispatchesEachCallback() {
        var saved = 0
        var discarded = 0
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                UnsavedChangesDialog(
                    onSave = { saved++ },
                    onDiscard = { discarded++ },
                    onCancel = { cancelled++ },
                )
            }
        }

        compose.onNodeWithText("저장").assertIsDisplayed().performClick()
        compose.onNodeWithText("저장 안 함").assertIsDisplayed().performClick()
        compose.onNodeWithText("취소").assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(1, saved)
            assertEquals(1, discarded)
            assertEquals(1, cancelled)
        }
    }

    @Test
    fun cleanBackExitsImmediately() {
        var backCount = 0
        compose.setContent {
            MaterialTheme {
                UnsavedChangesBackHandler(
                    hasUnsavedChanges = false,
                    onBack = { backCount++ },
                    onSave = {},
                    onDiscard = {},
                )
            }
        }

        pressBack()

        compose.runOnIdle { assertEquals(1, backCount) }
        compose.onNodeWithText("저장 안 함").assertDoesNotExist()
    }

    @Test
    fun dirtyBackOpensDialogAndCancelKeepsEditorOnScreen() {
        var backCount = 0
        compose.setContent {
            MaterialTheme {
                UnsavedChangesBackHandler(
                    hasUnsavedChanges = true,
                    onBack = { backCount++ },
                    onSave = {},
                    onDiscard = {},
                )
            }
        }

        pressBack()

        compose.onNodeWithText("저장 안 함").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, backCount) }
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("저장 안 함").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backCount) }
    }
}
