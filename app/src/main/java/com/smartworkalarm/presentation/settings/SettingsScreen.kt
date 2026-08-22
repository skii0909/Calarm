package com.smartworkalarm.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartworkalarm.domain.model.CalendarConnectionState

@Composable
fun SettingsScreen(state: CalendarConnectionState?, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineMedium)
        Text("캘린더 연결", style = MaterialTheme.typography.titleLarge)
        Text(if (state?.isConnected == true) "연결됨" else "연결된 Google 캘린더 없음")
        state?.calendars?.forEach { Text("• ${it.displayName}") }
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("캘린더 연결/권한 설정") }
        Text("Outlook Calendar — 준비 중")
        Text("사용법/도움말", style = MaterialTheme.typography.titleLarge)
        Text("• 알람을 여러 개 만들고 각각 시간과 반복 요일을 지정할 수 있습니다.")
        Text("• 캘린더 건너뛰기는 설정한 알람에만 적용됩니다.")
        Text("• 여러 키워드는 쉼표(,)로 구분하세요.")
        Text("• 일정 제목에 키워드가 하나라도 포함되면 해당 날짜의 알람을 건너뜁니다.")
        Text("• 매일 22시에 다음 날 알람 상태 변화를 한 번 알려드립니다.")
        Text("• 22시 이후에는 추가 안내 없이 실제 알람 시각에 캘린더를 최종 확인합니다.")
        Text("• 캘린더 조회에 실패하면 안전하게 알람이 울립니다.")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
    }
}
