package com.smartworkalarm.presentation.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.smartworkalarm.presentation.components.NightPulseSection

internal data class HelpSection(
    val title: String,
    val summary: String,
    val detail: String,
)

@Composable
internal fun ExpandableHelpCard(help: HelpSection) {
    var expanded by rememberSaveable(help.title) { mutableStateOf(false) }
    NightPulseSection(title = help.title, subtitle = help.summary) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "간단히 보기" else "자세히 보기")
        }
        if (expanded) {
            Text(
                help.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val HELP_SECTIONS = listOf(
    HelpSection(
        title = "일반 알람",
        summary = "한 번만 울리는 알람과 여러 요일 반복 알람을 만들 수 있습니다.",
        detail = "요일을 선택하지 않으면 가장 가까운 설정 시각에 한 번만 울리고 자동으로 꺼집니다. 요일을 선택하면 해당 요일마다 반복됩니다. 다시 울림 사용을 켜면 알람 화면을 3초간 길게 눌러 설정한 5~30분 뒤 같은 화면으로 다시 울립니다.",
    ),
    HelpSection(
        title = "캘린더 자동 건너뛰기",
        summary = "활성화한 알람은 선택한 캘린더의 일정만 읽습니다. 쉼표로 키워드를 나눌 수 있습니다.",
        detail = "캘린더 자동 건너뛰기는 켜진 알람에만 적용됩니다. 쉼표(,)로 나눈 키워드 중 하나라도 일정 제목에 포함되면 해당 날짜를 건너뜁니다. 선택한 캘린더의 일정만 읽습니다.",
    ),
    HelpSection(
        title = "교대근무",
        summary = "일정 제목 전체와 별칭이 정확히 맞는 근무 형태 하나만 울립니다.",
        detail = "앞뒤 공백과 대소문자를 무시한 일정 제목 전체가 등록 별칭과 정확히 일치해야 합니다. 한 근무 형태만 맞으면 해당 알람이 울리고, 일치 항목이 없거나 서로 다른 근무 형태가 충돌하면 그룹 전체를 건너뜁니다. 캘린더를 읽지 못한 경우에만 비상 기본 근무 알람을 사용합니다.",
    ),
    HelpSection(
        title = "22시 확인",
        summary = "다음 날 상태가 달라졌을 때만 22시에 알려드립니다.",
        detail = "매일 22시에 다음 날을 확인하고 제외·재개 상태가 바뀔 때만 알립니다. 지연된 확인은 자정 이후 보내지 않습니다. 실제 알람 시각에 Google은 기기 캘린더, Outlook은 마지막으로 동기화한 일정으로 최종 상태를 확인합니다.",
    ),
    HelpSection(
        title = "Outlook 연결",
        summary = "Calarm에서 Microsoft 계정으로 로그인하고 사용할 캘린더를 선택합니다.",
        detail = "일정은 읽기 전용으로 가져옵니다. 앱을 열거나 새로고침할 때 동기화하며, 백그라운드에서는 약 15분 주기로 시도합니다. 절전이나 통신 상태에 따라 늦어질 수 있습니다. 마지막 동기화가 6시간을 넘었거나 재로그인이 필요하면 일정을 읽지 못한 것으로 처리하여 안전 동작을 적용합니다.",
    ),
    HelpSection(
        title = "공휴일·알람음·다시 울림",
        summary = "공휴일 제외, 추천 알람음, 점점 크게, 5~30분 다시 울림을 알람별로 설정합니다.",
        detail = "대한민국 공휴일 2026~2031년을 지원하고 휴대폰의 로컬 캘린더로 임시공휴일을 보완합니다. 추천 음원은 서버나 다운로드 없이 앱에 포함되며 점점 크게 울릴 수 있습니다. 다시 울림은 5~30분으로 설정합니다.",
    ),
    HelpSection(
        title = "안전 동작",
        summary = "캘린더를 읽지 못해도 필요한 알람을 놓치지 않도록 안전하게 동작합니다.",
        detail = "일반 알람은 캘린더 조회 실패 시 울립니다. 교대 그룹은 비상 기본 근무 알람만 울리고, 비상 기본이 없으면 건너뜁니다. 알람 작동 진단의 최근 알람 실행 기록에서 수신·실행 요청·제외 여부를 확인할 수 있습니다. 실행 요청 기록은 실제로 소리가 들렸다는 보장은 아닙니다.",
    ),
    HelpSection(
        title = "Samsung·기기별 설정",
        summary = "절전 앱과 권한 설정에 따라 잠금화면 알람이 제한될 수 있습니다.",
        detail = "재부팅 후에는 PIN 등으로 한 번 잠금을 해제해야 알람을 복원할 수 있습니다. 강제 종료 후에는 앱을 다시 열어 주세요. Galaxy 등 기기의 절전·초절전 앱 제한, 알람 음량과 방해금지 설정을 확인하세요. 알림, 정확한 알람, 전체화면 알람, 배터리 설정은 알람 작동 진단에서 확인할 수 있습니다. 잠금화면과 Always On Display에서도 직접 시험하는 것을 권장합니다.",
    ),
)
