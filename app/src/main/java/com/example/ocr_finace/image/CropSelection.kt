package com.example.ocr_finace.image

data class NormalizedPoint(val x: Float, val y: Float) {
    fun constrained() = NormalizedPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

data class CropSelection(
    val corners: List<NormalizedPoint> = defaultCropCorners(),
    val rotation: Int = 0,
) {
    init {
        require(corners.size == 4)
    }
}

fun defaultCropCorners(): List<NormalizedPoint> = listOf(
    NormalizedPoint(0.06f, 0.06f),
    NormalizedPoint(0.94f, 0.06f),
    NormalizedPoint(0.94f, 0.94f),
    NormalizedPoint(0.06f, 0.94f),
)

internal fun encodeCropSelection(selection: CropSelection): String = buildList {
    add(selection.rotation.toString())
    selection.corners.forEach { point ->
        add(point.x.toString())
        add(point.y.toString())
    }
}.joinToString(",")

internal fun decodeCropSelection(value: String?): CropSelection {
    val parts = value?.split(',') ?: return CropSelection()
    if (parts.size != 9) return CropSelection()
    return runCatching {
        CropSelection(
            rotation = parts[0].toInt().let { ((it % 360) + 360) % 360 },
            corners = (0 until 4).map { index ->
                NormalizedPoint(
                    parts[1 + index * 2].toFloat(),
                    parts[2 + index * 2].toFloat(),
                ).constrained()
            },
        )
    }.getOrDefault(CropSelection())
}

fun rotateCropClockwise(selection: CropSelection): CropSelection {
    val rotated = selection.corners.map { point ->
        NormalizedPoint(1f - point.y, point.x)
    }
    return selection.copy(
        corners = listOf(rotated[3], rotated[0], rotated[1], rotated[2]),
        rotation = (selection.rotation + 90) % 360,
    )
}

fun isValidCrop(corners: List<NormalizedPoint>): Boolean {
    if (corners.size != 4) return false
    val area = corners.indices.sumOf { index ->
        val current = corners[index]
        val next = corners[(index + 1) % corners.size]
        (current.x * next.y - next.x * current.y).toDouble()
    }
    return kotlin.math.abs(area) / 2.0 >= 0.02 &&
        !segmentsIntersect(corners[0], corners[1], corners[2], corners[3]) &&
        !segmentsIntersect(corners[1], corners[2], corners[3], corners[0])
}

private fun segmentsIntersect(
    a: NormalizedPoint,
    b: NormalizedPoint,
    c: NormalizedPoint,
    d: NormalizedPoint,
): Boolean {
    fun direction(p: NormalizedPoint, q: NormalizedPoint, r: NormalizedPoint): Float =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val first = direction(a, b, c)
    val second = direction(a, b, d)
    val third = direction(c, d, a)
    val fourth = direction(c, d, b)
    return first * second < 0f && third * fourth < 0f
}
