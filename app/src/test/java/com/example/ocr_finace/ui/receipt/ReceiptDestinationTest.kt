package com.example.ocr_finace.ui.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptDestinationTest {
    @Test
    fun detailRoundTripsReceiptId() {
        val destination = ReceiptDestination.Detail("receipt-123")

        assertEquals(destination, ReceiptDestination.decode(destination.encode()))
    }

    @Test
    fun adjustRoundTripsReceiptId() {
        val destination = ReceiptDestination.Adjust("receipt-123")
        assertEquals(destination, ReceiptDestination.decode(destination.encode()))
    }

    @Test
    fun listAndSettingsRoundTrip() {
        assertEquals(
            ReceiptDestination.List,
            ReceiptDestination.decode(ReceiptDestination.List.encode()),
        )
        assertEquals(
            ReceiptDestination.Settings,
            ReceiptDestination.decode(ReceiptDestination.Settings.encode()),
        )
        assertEquals(
            ReceiptDestination.CashewSettings,
            ReceiptDestination.decode(ReceiptDestination.CashewSettings.encode()),
        )
    }

    @Test
    fun invalidOrEmptyDetailFallsBackToList() {
        assertEquals(ReceiptDestination.List, ReceiptDestination.decode("unknown"))
        assertEquals(ReceiptDestination.List, ReceiptDestination.decode("detail:"))
    }
}
