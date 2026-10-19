package com.kaskita.lokal.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.max

/** Fits a [RoundedPolygon] into whatever box it is clipped to. */
class RoundedPolygonShape(private val polygon: RoundedPolygon) : Shape {
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = polygon.calculateBounds()
        val width = bounds[2] - bounds[0]
        val height = bounds[3] - bounds[1]
        val path: Path = polygon.toPath().asComposePath()
        matrix.reset()
        matrix.scale(size.width / max(width, height), size.height / max(width, height))
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Organic containers for category icons: every category gets one of a few soft shapes so it can be recognised by silhouette
 * and not only by color. The choice is a pure function of the key, so a category keeps its shape everywhere.
 */
object ExpressiveShapes {
    val cookie: Shape = RoundedPolygonShape(RoundedPolygon.star(9, innerRadius = 0.82f, rounding = CornerRounding(0.3f), innerRounding = CornerRounding(0.3f)))
    val flower: Shape = RoundedPolygonShape(RoundedPolygon.star(6, innerRadius = 0.78f, rounding = CornerRounding(0.5f), innerRounding = CornerRounding(0.5f)))
    val squircle: Shape = RoundedPolygonShape(RoundedPolygon(4, rounding = CornerRounding(0.36f, smoothing = 0.6f)))
    val softPentagon: Shape = RoundedPolygonShape(RoundedPolygon(5, rounding = CornerRounding(0.3f)))
    val scallop: Shape = RoundedPolygonShape(RoundedPolygon.star(12, innerRadius = 0.9f, rounding = CornerRounding(0.4f), innerRounding = CornerRounding(0.4f)))

    private val all = listOf(cookie, squircle, flower, softPentagon, scallop)

    fun forKey(key: String): Shape = all[Math.floorMod(key.hashCode(), all.size)]
}
