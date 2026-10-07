package com.xiaomieu.toolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xiaomieu.toolkit.ui.AppRoot
import com.xiaomieu.toolkit.ui.theme.XiaomiEuToolKitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaomiEuToolKitTheme {
                AppRoot()
            }
        }
    }
}
