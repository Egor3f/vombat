package com.efprojects.vombatsu_client.battle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import kotlin.math.atan2

private const val FLIGHT_MS = 900
private const val ARRIVAL = 0.9f
private const val ICON_HEADING_DEGREES = 90f
private val BUG_SIZE = 40.dp
private val SHRINK_DISTANCE = 56.dp

fun bugTravel(progress: Float): Float = (progress / ARRIVAL).coerceIn(0f, 1f)

fun bugScale(remaining: Float, shrinkDistance: Float, minScale: Float): Float =
    (remaining / shrinkDistance).coerceIn(minScale, 1f)

private class FlightPath(val from: Offset, val to: Offset) {
    val length = (to - from).getDistance()
    val heading = Math.toDegrees(atan2(to.y - from.y, to.x - from.x).toDouble()).toFloat() + ICON_HEADING_DEGREES
}

@Stable
class BugFlight {
    internal var arena: LayoutCoordinates? = null
    internal var launcher: LayoutCoordinates? = null
    internal var target: LayoutCoordinates? = null
    internal val progress = Animatable(0f)
    private var path by mutableStateOf<FlightPath?>(null)

    val flying get() = path != null

    suspend fun fly() {
        val from = centerOf(launcher) ?: return
        val to = centerOf(target) ?: return
        path = FlightPath(from, to)
        progress.snapTo(0f)
        try {
            progress.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
        } finally {
            path = null
        }
    }

    private fun centerOf(node: LayoutCoordinates?): Offset? {
        val root = arena?.takeIf { it.isAttached } ?: return null
        val source = node?.takeIf { it.isAttached } ?: return null
        return root.localPositionOf(source, source.size.center.toOffset())
    }

    @Composable
    internal fun BoxScope.Bug() {
        val current = path ?: return
        Icon(
            Icons.Filled.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(BUG_SIZE)
                .graphicsLayer {
                    val travel = bugTravel(progress.value)
                    val position = lerp(current.from, current.to, travel)
                    val scale = bugScale(current.length * (1f - travel), SHRINK_DISTANCE.toPx(), 1f / BUG_SIZE.toPx())
                    translationX = position.x - size.width / 2
                    translationY = position.y - size.height / 2
                    scaleX = scale
                    scaleY = scale
                    rotationZ = current.heading
                },
        )
    }
}

fun Modifier.bugLauncher(flight: BugFlight) = onGloballyPositioned { flight.launcher = it }

fun Modifier.bugTarget(flight: BugFlight) = onGloballyPositioned { flight.target = it }

@Composable
fun BugFlightArena(flight: BugFlight, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.onGloballyPositioned { flight.arena = it }) {
        content()
        with(flight) { Bug() }
    }
}
