package com.smartworkalarm.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.smartworkalarm.presentation.components.PersistentSaveScaffold
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class PersistentSaveScaffoldTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun saveActionRemainsVisibleAndClickableAtBothScrollExtremes() {
        compose.setContent {
            WorkAlarmTheme {
                var saved by remember { mutableStateOf(false) }
                PersistentSaveScaffold(saveLabel = "저장", onSave = { saved = true }) {
                    Text("첫 번째 설정", Modifier.testTag("first-setting"))
                    repeat(30) { index ->
                        Text("설정 $index", Modifier.fillMaxWidth().height(56.dp))
                    }
                    Text("마지막 설정", Modifier.testTag("last-setting"))
                }
                if (saved) Text("저장됨")
            }
        }

        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed().performClick()
        compose.onNodeWithText("저장됨").assertIsDisplayed()
        compose.onNodeWithTag("persistent-save-content").performScrollToNode(hasTestTag("last-setting"))
        compose.onNodeWithTag("last-setting").assertIsDisplayed()
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed().performClick()
        compose.onNodeWithText("저장됨").assertIsDisplayed()
    }

    @Test
    fun saveActionStaysAboveTheSuppliedNavigationBarInset() {
        val navigationBarInset = 40.dp
        var navigationBarInsetPx = 0f

        compose.setContent {
            WorkAlarmTheme {
                navigationBarInsetPx = with(LocalDensity.current) { navigationBarInset.toPx() }
                PersistentSaveScaffold(
                    saveLabel = "저장",
                    onSave = {},
                    scrollState = rememberScrollState(),
                    bottomBarInsets = WindowInsets(bottom = navigationBarInset),
                ) {
                    Text("설정")
                }
            }
        }

        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val actionBottom = compose.onNodeWithTag("persistent-save-action")
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "save action bottom=$actionBottom must clear navigation inset=$navigationBarInsetPx from root bottom=$rootBottom",
            actionBottom <= rootBottom - navigationBarInsetPx,
        )
    }
}
