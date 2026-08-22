package com.smartworkalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.smartworkalarm.presentation.navigation.WorkAlarmNavHost
import com.smartworkalarm.presentation.theme.WorkAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorkAlarmTheme { WorkAlarmNavHost() } }
    }
}
