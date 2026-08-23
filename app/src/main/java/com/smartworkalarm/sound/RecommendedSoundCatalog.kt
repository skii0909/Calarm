package com.smartworkalarm.sound

import com.smartworkalarm.R

data class RecommendedSound(
    val id: String,
    val title: String,
    val description: String,
    val rawResource: Int,
) {
    val uri: String get() = "android.resource://com.smartworkalarm/raw/$id"
}

object RecommendedSoundCatalog {
    val all = listOf(
        RecommendedSound("sunrise_pop", "선라이즈 팝", "밝고 경쾌한 기상음", R.raw.sunrise_pop),
        RecommendedSound("soft_marimba", "소프트 마림바", "부드러운 우드 톤", R.raw.soft_marimba),
        RecommendedSound("digital_pulse", "디지털 펄스", "또렷한 전자 리듬", R.raw.digital_pulse),
        RecommendedSound("lofi_bell", "로파이 벨", "차분한 벨 사운드", R.raw.lofi_bell),
        RecommendedSound("bright_chime", "브라이트 차임", "맑고 높은 차임", R.raw.bright_chime),
        RecommendedSound("minimal_beat", "미니멀 비트", "간결한 반복 비트", R.raw.minimal_beat),
    )
}
