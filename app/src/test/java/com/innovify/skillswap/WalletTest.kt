package com.innovify.skillswap

import com.google.gson.Gson
import com.innovify.skillswap.reputation.domain.CreditTransaction
import com.innovify.skillswap.reputation.domain.Wallet
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
    fun title_is_built_in_spanish_from_the_movement_data_not_from_the_backend_text() {
        val case = transaction("Earned", 25).copy(description = "Verification case resolved", relatedCaseId = 1)
        val demo = transaction("Earned", 40).copy(description = "Final demonstration reviewed", relatedDemonstrationId = 4)
        val other = transaction("Earned", 10).copy(description = "Anything")
        val badge = transaction("Redeemed", 200).copy(description = "Redeemed: Badge", redemptionItem = "Badge")
        val unknown = transaction("Redeemed", 99).copy(description = "Redeemed: New", redemptionItem = "NewItem")

        assertEquals("Caso #1 resuelto", case.title())
        assertEquals("Demostración final revisada", demo.title())
        assertEquals("SkillCredits ganados", other.title())
        assertEquals("Canje: Insignia", badge.title())
        assertEquals("Canje de SkillCredits", unknown.title())
    }

    @Test
    fun demonstration_id_parses_from_backend_json() {
        val raw = """{"id":8,"walletId":3,"amount":40,"type":"Earned","relatedDemonstrationId":4}"""
        val parsed = Gson().fromJson(raw, CreditTransaction::class.java)
        assertEquals(4, parsed.relatedDemonstrationId)
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
