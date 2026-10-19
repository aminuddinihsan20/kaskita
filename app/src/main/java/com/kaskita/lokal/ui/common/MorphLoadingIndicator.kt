package com.kaskita.lokal.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.kaskita.lokal.R

/** A loading indicator that morphs through the category shapes while it slowly turns. */
@Composable
fun MorphLoadingIndicator(modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val shapes = remember {
        listOf(
            RoundedPolygon.star(9, innerRadius = 0.82f, rounding = CornerRounding(0.3f), innerRounding = CornerRounding(0.3f)),
            RoundedPolygon(4, rounding = CornerRounding(0.36f, smoothing = 0.6f)),
            RoundedPolygon.star(6, innerRadius = 0.78f, rounding = CornerRounding(0.5f), innerRounding = CornerRounding(0.5f)),
            RoundedPolygon(5, rounding = CornerRounding(0.3f)),
        )
    }
    val morphs = remember { shapes.mapIndexed { i, a -> Morph(a, shapes[(i + 1) % shapes.size]) } }
    val transition = rememberInfiniteTransition(label = "loading")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 800 * shapes.size, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val turn by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    val description = stringResource(R.string.common_loading)

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val index = phase.toInt().coerceIn(0, shapes.lastIndex)
        val progress = FastOutSlowInEasing.transform(phase - index)
        val path = morphs[index].toPath(progress).asComposePath()
        val matrix = Matrix().apply {
            translate(this@Canvas.size.width / 2f, this@Canvas.size.height / 2f)
            scale(this@Canvas.size.minDimension / 2f * 0.92f, this@Canvas.size.minDimension / 2f * 0.92f)
        }
        path.transform(matrix)
        rotate(turn) { drawPath(path, color) }
    }
}
