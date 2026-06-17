package com.grailpay.banklink.internal.net

import java.util.concurrent.atomic.AtomicReference

internal class TokenStore {
    private val merchant = AtomicReference<String?>(null)
    private val link = AtomicReference<String?>(null)

    var merchantToken: String?
        get() = merchant.get()
        set(value) { merchant.set(value) }

    var linkToken: String?
        get() = link.get()
        set(value) { link.set(value) }

    fun clear() {
        merchant.set(null)
        link.set(null)
    }
}
