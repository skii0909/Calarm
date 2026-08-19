package com.smartworkalarm.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var outlookMessage by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onComplete()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Work Alarm", style = MaterialTheme.typography.displaySmall)
        Text(
            "휴가를 캘린더에 등록하면 출근 알람을 알아서 건너뜁니다.\n휴가가 끝나면 다시 원래대로 울립니다.",
            modifier = Modifier.padding(vertical = 24.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = {
                launcher.launch(
                    buildList {
                        add(Manifest.permission.READ_CALENDAR)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Google Calendar 연결") }
        OutlinedButton(
            onClick = { outlookMessage = "Outlook Calendar는 준비 중입니다." },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Outlook Calendar (준비 중)") }
        outlookMessage?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        Text(
            "캘린더 권한을 허용하지 않아도 알람은 정상적으로 울립니다. 휴가 자동 건너뛰기만 사용할 수 없습니다.",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
