package com.smartworkalarm.presentation

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.smartworkalarm.MainActivity
import com.smartworkalarm.presentation.alarmedit.AlarmEditScreen
import com.smartworkalarm.presentation.alarmedit.AlarmEditViewModel
import com.smartworkalarm.presentation.shift.ShiftGroupEditViewModel
import com.smartworkalarm.presentation.shift.ShiftGroupScreen
import com.smartworkalarm.presentation.shift.ShiftTypeEditScreen
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.junit.Rule
import org.junit.Test

/** Captures the real editor routes and exercises them under narrow/large-text constraints. */
class DesignEditorGalleryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun alarmEditorGalleryAndLargeTextSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: AlarmEditViewModel
            scenario.onActivity { viewModel = ViewModelProvider(it)[AlarmEditViewModel::class.java] }
            gallery(scenario, "alarm-editor", "알람 활성화", "알람 이름을 입력하세요") {
                AlarmEditScreen(viewModel = viewModel, alarmId = 0, onDone = {})
            }
        }
    }

    @Test
    fun shiftGroupGalleryAndLargeTextSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: ShiftGroupEditViewModel
            scenario.onActivity { viewModel = ViewModelProvider(it)[ShiftGroupEditViewModel::class.java] }
            gallery(scenario, "shift-group", "＋ 근무 형태 추가", "그룹 이름을 입력하세요") {
                ShiftGroupScreen(viewModel = viewModel, groupId = 0, onDone = {}, onEditShiftType = { _, _ -> })
            }
        }
    }

    @Test
    fun shiftTypeGalleryAndLargeTextSave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var viewModel: ShiftGroupEditViewModel
            val groupId = AtomicLong(0)
            scenario.onActivity {
                viewModel = ViewModelProvider(it)[ShiftGroupEditViewModel::class.java]
                viewModel.loadGroup(0)
                viewModel.onGroupNameChanged("디자인 검증용 임시 그룹")
                viewModel.createDraftAndAddShiftType(groupId::set)
            }
            try {
                compose.waitUntil(10_000) { groupId.get() != 0L }
                gallery(scenario, "shift-type", "알람 활성화", "근무 형태 이름을 입력하세요",
                    ready = { viewModel.shiftTypeState.value.groupId == groupId.get() }) {
                    ShiftTypeEditScreen(viewModel = viewModel, groupId = groupId.get(), shiftTypeId = 0, onDone = {})
                }
            } finally {
                scenario.onActivity { viewModel.cancelDraft() }
                compose.waitUntil(10_000) { viewModel.state.value.deleted }
            }
        }
    }

    private fun gallery(
        scenario: ActivityScenario<MainActivity>,
        name: String,
        lastControl: String,
        validationMessage: String,
        ready: () -> Boolean = { true },
        content: @Composable () -> Unit,
    ) {
        val largeText = mutableStateOf(false)
        scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText.value) 2f else 1f)) {
                    WorkAlarmTheme {
                        Box(Modifier.fillMaxHeight().widthIn(max = if (largeText.value) 320.dp else 440.dp)) {
                            content()
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000, ready)
        compose.waitForIdle()
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed()
        saveScreen("$name-normal-top.png")
        compose.onNodeWithTag("persistent-save-content").performScrollToNode(hasText(lastControl))
        compose.onNodeWithText(lastControl).assertIsDisplayed()
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed()
        saveScreen("$name-normal-bottom.png")

        compose.runOnIdle { largeText.value = true }
        compose.onNodeWithTag("persistent-save-content").performScrollToNode(hasText("‹ 뒤로"))
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed()
        saveScreen("$name-320dp-font2-top.png")
        compose.onNodeWithTag("persistent-save-content").performScrollToNode(hasText(lastControl))
        compose.onNodeWithText(lastControl).assertIsDisplayed()
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed()
        saveScreen("$name-320dp-font2-bottom.png")
        compose.onNodeWithTag("persistent-save-action").performClick()
        // Blank-name validation proves the visible save button still invokes the real ViewModel.
        compose.onNodeWithTag("persistent-save-content").performScrollToNode(hasText(validationMessage))
        compose.onNodeWithText(validationMessage).assertIsDisplayed()
        compose.onNodeWithTag("persistent-save-action").assertIsDisplayed()
        saveScreen("$name-320dp-font2-validation.png")
    }

    private fun saveScreen(name: String) {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(path).mkdirs()
        File(path, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
