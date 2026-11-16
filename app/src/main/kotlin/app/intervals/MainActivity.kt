package app.intervals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.intervals.core.Block
import app.intervals.core.Series
import app.intervals.core.SeriesIcon
import app.intervals.core.Workout
import app.intervals.core.formatDuration
import app.intervals.core.totalSec

// Palette agreed in the mockups: purple accent, pure black in dark mode.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    background = Color.Black,
    surface = Color(0xFF1A1C21),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    background = Color(0xFFF6F5FA),
    surface = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                Placeholder()
            }
        }
    }
}

@Composable
private fun Placeholder() {
    // Temporary first screen: proves the app builds and uses the core module.
    val sample = Workout(
        name = "hangboard 70%",
        warmupSec = 5,
        rounds = 4,
        restBetweenRoundsSec = 60,
        blocks = listOf(
            Block.Repeat(3, listOf(Series("hang", 5, 0xFFE91E63, SeriesIcon.JUMP), Series("rest", 5, 0xFFFFC107))),
            Block.Single(Series("hang", 5, 0xFFE91E63, SeriesIcon.JUMP)),
        ),
    )
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Intervals", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "${sample.name} · ${formatDuration(sample.totalSec)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
