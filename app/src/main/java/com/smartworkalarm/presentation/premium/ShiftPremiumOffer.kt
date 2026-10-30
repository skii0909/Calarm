package com.smartworkalarm.presentation.premium

data class ShiftPremiumOffer(
    val regularPriceWon: Int,
    val launchPriceWon: Int,
    val isLifetime: Boolean,
    val headline: String,
    val purchaseLabel: String,
) {
    companion object {
        val launch = ShiftPremiumOffer(
            regularPriceWon = 9_900,
            launchPriceWon = 5_900,
            isLifetime = true,
            headline = "근무 스케줄에 맞춰 알람이 자동으로 켜지고 꺼집니다",
            purchaseLabel = "5,900원에 평생 이용하기",
        )
    }
}
