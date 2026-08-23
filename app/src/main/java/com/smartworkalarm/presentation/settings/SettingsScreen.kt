package com.smartworkalarm.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Text("• 교대 알람 그룹은 캘린더 제목 전체가 등록한 별칭과 정확히 같을 때만 선택됩니다. 예: `야간`은 `야간`과 일치하지만 `야간 근무 후 회식`에는 적용되지 않습니다.")
        Text("• 교대 그룹 안에서는 제목 별칭 일부가 겹치는 근무 형태를 저장할 수 없습니다. 중복된 별칭을 고쳐 저장하세요.")
        Text("• 내일 캘린더에 등록한 근무 형태가 없으면 해당 교대 그룹 알람은 울리지 않으며, 일반 알람에는 영향을 주지 않습니다.")
        Text("• 같은 날짜에 서로 다른 근무 형태가 함께 있으면 충돌로 판단해 해당 교대 그룹 알람은 울리지 않습니다. 캘린더 일정을 확인하세요.")
        Text("• 교대 근무를 조회하지 못하면 지정한 비상 기본 근무 형태 알람만 울립니다. 비상 기본 알람이 없으면 교대 그룹 알람은 울리지 않습니다.")
        Text("• 교대 근무가 바뀌거나 근무 없음·충돌 상태가 새로 생긴 경우에만 22시에 안내합니다. 같은 상태가 이어지면 반복 안내하지 않습니다.")
        Text("• 공휴일 자동 끄기는 알람별로 선택하며, 대한민국 공휴일 2026~2031년을 지원합니다.")
        Text("• 임시공휴일은 연결된 휴대폰 캘린더 정보로 보완합니다.")
        Text("• 추천 벨소리는 외부 서버나 다운로드 없이 앱에 포함된 자체 제작 음원입니다.")
        Text("• 다시 울림은 알람마다 5~30분으로 설정할 수 있습니다.")
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
    }
}
