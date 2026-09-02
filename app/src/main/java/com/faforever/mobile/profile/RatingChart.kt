package com.faforever.mobile.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun RatingChart(
    points: List<RatingPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (points.isEmpty()) "No rated games" else "Not enough games for a graph",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    val minTime = points.first().timeMillis
    val maxTime = points.last().timeMillis
    val minRating = points.minOf { it.rating }
    val maxRating = points.maxOf { it.rating }
    // Pad the rating range so the line doesn't hug the edges
    val ratingPad = ((maxRating - minRating) * 0.1).coerceAtLeast(20.0)
    val loRating = floor((minRating - ratingPad) / 100.0) * 100.0
    val hiRating = ceil((maxRating + ratingPad) / 100.0) * 100.0

    val dateFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        val leftPad = 90f
        val bottomPad = 40f
        val chartWidth = size.width - leftPad
        val chartHeight = size.height - bottomPad

        fun xFor(time: Long): Float =
            leftPad + chartWidth * ((time - minTime).toFloat() / (maxTime - minTime).coerceAtLeast(1).toFloat())

        fun yFor(rating: Double): Float =
            chartHeight * (1f - ((rating - loRating) / (hiRating - loRating)).toFloat())

        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt(),
            )
            textSize = 11.sp.toPx()
            isAntiAlias = true
        }

        // Horizontal grid lines with rating labels
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val rating = loRating + (hiRating - loRating) * i / gridSteps
            val y = yFor(rating)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawContext.canvas.nativeCanvas.drawText(
                rating.toInt().toString(),
                4f,
                y + 4.sp.toPx(),
                labelPaint,
            )
        }

        // Time labels: start, middle, end
        listOf(minTime, (minTime + maxTime) / 2, maxTime).forEachIndexed { i, t ->
            val label = dateFormat.format(Date(t))
            val textWidth = labelPaint.measureText(label)
            val x = when (i) {
                0 -> leftPad
                1 -> leftPad + chartWidth / 2 - textWidth / 2
                else -> size.width - textWidth
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 6f,
                labelPaint,
            )
        }

        // Rating line + gradient fill under it
        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(p.timeMillis)
            val y = yFor(p.rating)
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartHeight)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(xFor(points.last().timeMillis), chartHeight)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillTop, Color.Transparent),
                startY = 0f,
                endY = chartHeight,
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Latest-point marker
        val last = points.last()
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(xFor(last.timeMillis), yFor(last.rating)),
        )
    }
}
