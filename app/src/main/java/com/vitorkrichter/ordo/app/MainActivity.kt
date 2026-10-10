package com.vitorkrichter.ordo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vitorkrichter.ordo.core.designsystem.theme.OrdoTheme
import com.vitorkrichter.ordo.core.navigation.OrdoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrdoTheme {
                OrdoApp()
            }
        }
    }
}
