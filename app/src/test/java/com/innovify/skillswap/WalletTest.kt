package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.model.beans.CreditTransaction
import com.innovify.skillswap.model.beans.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTest {

    private fun transaction(type: String, amount: Int) = CreditTransaction(
        id = 1, walletId = 2, amount = amount, type = type, description = "Caso resuelto",
        relatedCaseId = null, createdAt = "2026-10-22T15:30:00Z", redemptionItem = null
    )

    @Test
    fun earned_transaction_shows_plus_sign() {
        val earned = transaction("Earned", 40)
        assertTrue(earned.isEarned())
        assertEquals("+40", earned.signedAmount())
        assertEquals("Ganado", earned.typeLabel())
    }

    @Test
    fun redeemed_transaction_shows_minus_sign() {
        val redeemed = transaction("Redeemed", 200)
        assertFalse(redeemed.isEarned())
        assertEquals("\u2212200", redeemed.signedAmount())
        assertEquals("Canjeado", redeemed.typeLabel())
    }

    @Test
    fun wallet_and_transactions_parse_from_backend_json() {
        val wallet = Gson().fromJson("""{"id":3,"walletOwnerId":9,"balance":65}""", Wallet::class.java)
        assertEquals(65, wallet.balance)

        val raw = """{"id":7,"walletId":3,"amount":25,"type":"Earned","description":"Quiz","createdAt":"2026-10-22T15:30:00"}"""
        val parsed = Gson().fromJson(raw, CreditTransaction::class.java)
        assertEquals(25, parsed.amount)
        assertNull(parsed.relatedCaseId)
        assertNull(parsed.redemptionItem)
    }
}
