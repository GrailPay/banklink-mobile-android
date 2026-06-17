package com.grailpay.banklink.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grailpay.banklink.sample.demo.DemoApp
import com.grailpay.banklink.sample.ui.theme.GrailPayBankLinkSampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrailPayBankLinkSampleTheme(dynamicColor = false) {
                DemoApp()
            }
        }
    }
}