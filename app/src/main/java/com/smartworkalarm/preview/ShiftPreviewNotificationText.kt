package com.smartworkalarm.preview

import com.smartworkalarm.domain.shift.ShiftDayEvaluation

object ShiftPreviewNotificationText {
    fun row(change: ShiftPreviewChange): String = when (val evaluation = change.currentEvaluation) {
        is ShiftDayEvaluation.Matched -> "내일 ${evaluation.name} 근무 알람이 울립니다."
        ShiftDayEvaluation.None -> "내일 교대 알람이 울리지 않습니다."
        is ShiftDayEvaluation.Conflict -> {
            val names = evaluation.matches
                .distinctBy { it.shiftTypeId }
                .sortedBy { it.shiftTypeId }
                .joinToString("과 ") { it.name }
            "내일 ${names} 일정이 함께 등록되어 있습니다. 캘린더 근무 일정을 확인해 주세요."
        }
        ShiftDayEvaluation.ReadFailure -> "내일 근무 일정을 읽을 수 없습니다. 캘린더 접근 권한을 확인해 주세요."
    }
}
