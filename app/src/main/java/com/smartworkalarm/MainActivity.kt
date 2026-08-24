package com.smartworkalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.smartworkalarm.alarm.FullScreenAlarmAccess
import com.smartworkalarm.presentation.navigation.WorkAlarmNavHost
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var showFullScreenAlarmPrompt by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkAlarmTheme {
                WorkAlarmNavHost()
                if (showFullScreenAlarmPrompt) {
                    AlertDialog(
                        onDismissRequest = { showFullScreenAlarmPrompt = false },
                        title = { Text("잠금화면 알람 허용") },
                        text = { Text("알람이 울릴 때 잠금화면 위에 해제 화면을 표시하려면 전체화면 알람을 허용해 주세요.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showFullScreenAlarmPrompt = false
                                startActivity(FullScreenAlarmAccess.settingsIntent(this))
                            }) { Text("설정 열기") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showFullScreenAlarmPrompt = false }) { Text("나중에") }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showFullScreenAlarmPrompt = !FullScreenAlarmAccess.canUse(this)
    }
}
