package com.rython.authenticator.core

import com.rython.authenticator.data.Account

/**
 * Collects accounts from a Google Authenticator "Transfer accounts → Export accounts"
 * session. Large exports are split over several QR codes, so every frame is merged
 * here until the user finishes.
 */
class TransferSession {

    private val entries = LinkedHashMap<String, Account>()

    /** How many QR codes the export contains (1 when unknown). */
    var batchSize: Int = 0
        private set

    /** How many QR codes have been received so far. */
    var batchesReceived: Int = 0
        private set

    val accounts: List<Account> get() = entries.values.toList()
    val isEmpty: Boolean get() = entries.isEmpty()

    /** True when the export was split over several QR codes. */
    val isBatched: Boolean get() = batchSize > 1

    /**
     * Accepts one scanned/pasted code: either a Google Auth migration frame or a
     * plain `otpauth://` link. Returns how many accounts were new.
     */
    fun accept(raw: String): Int {
        if (raw.trim().startsWith("otpauth://", ignoreCase = true)) {
            // A plain single account can also be transferred by hand.
            return addAll(OtpUri.parse(raw))
        }
        return acceptFrame(OtpUri.parseMigrationFrame(raw))
    }

    /** Accepts the raw base64 payload of a migration frame (testable without Android). */
    fun acceptMigrationData(data: String): Int = acceptFrame(OtpUri.parseMigrationData(data))

    fun acceptFrame(frame: MigrationFrame): Int {
        if (frame.batchSize > 0) {
            batchSize = frame.batchSize
            batchesReceived = (batchesReceived + 1).coerceAtMost(batchSize)
        }
        return addAll(frame.accounts)
    }

    private fun addAll(accounts: List<Account>): Int {
        var added = 0
        for (account in accounts) {
            val key = "${account.secret}|${account.issuer}|${account.name}"
            if (entries.putIfAbsent(key, account) == null) added++
        }
        return added
    }

    fun clear() {
        entries.clear()
        batchSize = 0
        batchesReceived = 0
    }

    companion object {
        fun keyOf(account: Account): String =
            "${account.secret}|${account.issuer}|${account.name}"

        /** Drops accounts that already exist in the vault. */
        fun newOnly(imported: List<Account>, existing: Collection<Account>): List<Account> {
            val known = existing.mapTo(HashSet()) { keyOf(it) }
            return imported.filter { keyOf(it) !in known }
        }
    }
}
