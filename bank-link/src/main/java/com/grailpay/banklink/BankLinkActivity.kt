package com.grailpay.banklink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grailpay.banklink.ui.BankLinkContent

class BankLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Session + listener live in the controller, not the Intent — keeps tokens out of an
        // Intent the framework can persist to disk. After process death the controller is reset,
        // so both are null and we just finish.
        val session = GrailPayBankLink.activeSession()
        val listener = GrailPayBankLink.activeListener()
        if (session == null || listener == null) {
            finish()
            return
        }
        GrailPayBankLink.registerActivity(this)
        setContent {
            BankLinkContent(
                session = session,
                onFinish = { finish() },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Must run before unregisterActivity(): onActivityFinished() guards on
        // `bankLinkActivity == this`, which unregisterActivity() nulls. Reverse the order and the
        // guard always fails, the controller stays stuck in ActivityOpen, and the next init() is
        // dropped as init_while_activity_open.
        if (isFinishing) {
            GrailPayBankLink.onActivityFinished(this)
        }
        GrailPayBankLink.unregisterActivity(this)
    }
}
