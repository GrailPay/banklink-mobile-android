package com.grailpay.banklink.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.grailpay.banklink.GrailPayBankLink

// Receives the OAuth-return deep-link, forwards the URL into the live QuilttConnectorWebView via
// the SDK, and finishes immediately. The active BankLinkActivity is still on the back stack, so
// finishing this transparent activity returns the user there with the connector-flow resuming.
//
// Production: this activity should be claimed by an HTTPS App Link with autoVerify="true" + a
// hosted /.well-known/assetlinks.json (see GrailPayBankLink.handleOAuthReturn KDoc).
//
// Local testing without domain ownership: direct-target via adb. See README and the comment block
// at the bottom of MainActivity for the adb command.
class OAuthReturnActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.let { GrailPayBankLink.handleOAuthReturn(it) }
        finish()
    }
}