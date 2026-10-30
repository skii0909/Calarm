package com.smartworkalarm.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onOpenCalendarSettings: () -> Unit = onComplete,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Calarm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "캘람 · 캘린더와 알람의 조합",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("일정에 맞춰,\n여유로운 아침.",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "휴가를 캘린더에 등록하면 출근 알람을 알아서 건너뜁니다.\n휴가가 끝나면 다시 원래대로 울립니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("캘린더와 함께하는 아침", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("07:00", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("출근 알람", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("휴가에는 쉬어가기", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        Button(
            onClick = onOpenCalendarSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Google Calendar 연결") }
        OutlinedButton(
            onClick = onOpenCalendarSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Outlook 연결") }
        TextButton(onClick = onComplete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("캘린더 없이 시작") }
        Text(
            "Google은 휴대폰에 동기화된 캘린더를, Outlook은 Microsoft 로그인으로 연결한 캘린더를 사용합니다. 연결 없이 일반 알람만 사용할 수도 있습니다.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
