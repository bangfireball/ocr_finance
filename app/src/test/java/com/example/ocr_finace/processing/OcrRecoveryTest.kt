package com.example.ocr_finace.processing

import androidx.work.WorkInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrRecoveryTest {
    @Test
    fun activeWorkMustNotBeDuplicated() {
        assertTrue(isUnfinishedWork(WorkInfo.State.ENQUEUED))
        assertTrue(isUnfinishedWork(WorkInfo.State.RUNNING))
        assertTrue(isUnfinishedWork(WorkInfo.State.BLOCKED))
    }

    @Test
    fun terminalWorkAllowsOrphanRecovery() {
        assertFalse(isUnfinishedWork(WorkInfo.State.SUCCEEDED))
        assertFalse(isUnfinishedWork(WorkInfo.State.FAILED))
        assertFalse(isUnfinishedWork(WorkInfo.State.CANCELLED))
    }
}
