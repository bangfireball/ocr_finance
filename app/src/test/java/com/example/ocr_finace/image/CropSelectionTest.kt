package com.example.ocr_finace.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropSelectionTest {
    @Test
    fun selectionRoundTripsPersistedCoordinates() {
        val selection = CropSelection(defaultCropCorners(), rotation = 90)
        assertEquals(selection, decodeCropSelection(encodeCropSelection(selection)))
    }

    @Test
    fun decodeFallsBackForInvalidValues() {
        assertEquals(CropSelection(), decodeCropSelection("invalid"))
    }

    @Test
    fun clockwiseRotationTransformsEveryCorner() {
        val rotated = rotateCropClockwise(CropSelection()).corners
        defaultCropCorners().zip(rotated).forEach { (expected, actual) ->
            assertEquals(expected.x, actual.x, 0.0001f)
            assertEquals(expected.y, actual.y, 0.0001f)
        }
        assertEquals(90, rotateCropClockwise(CropSelection()).rotation)
    }

    @Test
    fun crossingAndTinyShapesAreRejected() {
        assertTrue(isValidCrop(defaultCropCorners()))
        assertFalse(
            isValidCrop(
                listOf(
                    NormalizedPoint(0f, 0f),
                    NormalizedPoint(1f, 1f),
                    NormalizedPoint(1f, 0f),
                    NormalizedPoint(0f, 1f),
                ),
            ),
        )
        assertFalse(
            isValidCrop(
                listOf(
                    NormalizedPoint(0f, 0f),
                    NormalizedPoint(0.01f, 0f),
                    NormalizedPoint(0.01f, 0.01f),
                    NormalizedPoint(0f, 0.01f),
                ),
            ),
        )
    }
}
