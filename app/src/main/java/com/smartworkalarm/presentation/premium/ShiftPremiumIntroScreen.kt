package com.smartworkalarm.presentation.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.smartworkalarm.presentation.components.GradientHero
import com.smartworkalarm.presentation.components.FeaturePill
import com.smartworkalarm.presentation.components.NightPulseSection

@Composable
fun ShiftPremiumIntroScreen(
    onBack: () -> Unit,
    onPreview: () -> Unit,
    offer: ShiftPremiumOffer = ShiftPremiumOffer.launch,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("CALARM PLUS", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onBack) { Text("← 돌아가기") }
                }
            }
            item {
                GradientHero {
                    FeaturePill("근무 스케줄 자동 알람")
                    Text(offer.headline, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("매일 알람을 다시 맞추지 않아도 됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text("캘린더만 바꾸면 알람도 자동으로", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "주간·야간·새벽처럼 출근 시간이 달라져도 Calarm이 오늘 필요한 알람 하나를 자동으로 켜고, 나머지는 자동으로 끕니다.",
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                NightPulseSection(title = "달라지는 근무, 맞춰지는 알람", subtitle = "일정에 따라 이렇게 동작해요") {
                    ScheduleExample("월요일 · 주간", "오전 6시 알람 자동 켜기")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ScheduleExample("화요일 · 야간", "오후 5시 알람 자동 켜기")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ScheduleExample("수요일 · 휴가", "모닝 알람 자동으로 쉬기")
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("한 번 구매하면 계속 사용", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("정가 9,900원", textDecoration = TextDecoration.LineThrough,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("출시 특별가 5,900원", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("월 구독이 아닌 개인용 평생 이용권입니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("${offer.purchaseLabel} · 출시 준비 중")
                }
                OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) {
                    Text("기능 미리 사용하기")
                }
                Text(
                    "현재 테스트 버전에서는 결제 없이 교대 그룹을 미리 사용할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun ScheduleExample(schedule: String, result: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(schedule, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(result, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
