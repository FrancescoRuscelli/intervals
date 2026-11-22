package app.intervals.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.intervals.core.SeriesIcon

/**
 * The line icons of the mockups (24×24, round caps), drawn from the same SVG paths.
 * No icon library: keeps the app small.
 */
object AppIcons {
    private fun stroke(path: String, width: Float = 2f) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(path).toNodes(),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun fill(path: String) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

    val Intervals = stroke("M12 8v5l3 2M9 2h6M12 4a9 9 0 1 0 0 18a9 9 0 0 0 0-18")
    val Gym = stroke("M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12")
    val Journal = stroke("M3.5 7.5a2.5 2.5 0 0 1 2.5-2.5h12a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-12a2.5 2.5 0 0 1-2.5-2.5zM3.5 10h17M8 3v4M16 3v4")
    val Stats = stroke("M5 20v-8M12 20V5M19 20v-5", 2.2f)
    val Settings = stroke("M12 9a3 3 0 1 0 0 6a3 3 0 0 0 0-6M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
    val Import = stroke("M12 3v12M7 10l5 5 5-5M4 21h16")
    val Search = stroke("M11 4a7 7 0 1 0 0 14a7 7 0 0 0 0-14M20 20l-3.5-3.5")
    val Plus = stroke("M12 5v14M5 12h14", 2.4f)
    val Minus = stroke("M5 12h14", 2.4f)
    val Close = stroke("M6 6l12 12M18 6L6 18")
    val Back = stroke("M15 5l-7 7 7 7", 2.2f)
    val Check = stroke("M5 12.5l4.5 4.5L19 7.5", 2.8f)
    val Edit = stroke("M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4")
    val Delete = stroke("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13", 2.2f)
    val Repeat = stroke("M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3", 2.2f)
    val Flame = stroke("M12 3c1 4 5 5.5 5 10a5 5 0 0 1-10 0c0-2.5 1.5-4 2-6 1.5 1.5 3 2.5 3 5")
    val Hourglass = stroke("M7 4h10M7 20h10M8 4c0 5 8 5 8 8s-8 3-8 8M16 4c0 5-8 5-8 8")
    val Up = stroke("M6 15l6-6 6 6", 2.2f)
    val Down = stroke("M6 9l6 6 6-6", 2.2f)
    val Play = fill("M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z")
    val More = fill("M12 3.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 0 0 0-3.6M12 10.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 0 0 0-3.6M12 17.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 0 0 0-3.6")

    private val Jump = stroke("M12 18V5M6.5 10.5L12 5l5.5 5.5M5 21h14", 2.2f)
    private val Run = stroke("M14 4.5a1.5 1.5 0 1 0 0 .01M6 12l3-3h5l2 4h3M10 9l-2 5 4 2v5M8 14l-3 5", 2.2f)
    private val Stand = stroke("M12 3.5a1.5 1.5 0 1 0 0 .01M12 7v7M8 10h8M12 14l-3 7M12 14l3 7", 2.2f)
    private val Cool = stroke("M12 2v20M3.5 7l17 10M20.5 7l-17 10")

    fun series(icon: SeriesIcon): ImageVector = when (icon) {
        SeriesIcon.JUMP -> Jump
        SeriesIcon.RUN -> Run
        SeriesIcon.STAND -> Stand
        SeriesIcon.WEIGHT -> Gym
        SeriesIcon.WARMUP -> Flame
        SeriesIcon.COOL -> Cool
    }

    fun label(icon: SeriesIcon): String = when (icon) {
        SeriesIcon.JUMP -> "Jump"
        SeriesIcon.RUN -> "Run"
        SeriesIcon.STAND -> "Stand"
        SeriesIcon.WEIGHT -> "Weight"
        SeriesIcon.WARMUP -> "Warm-up"
        SeriesIcon.COOL -> "Cool"
    }
}
