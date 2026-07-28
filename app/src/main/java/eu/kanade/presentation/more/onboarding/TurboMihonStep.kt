package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.presentation.core.components.material.padding as themePadding

internal class TurboMihonStep : OnboardingStep {

    override val isComplete: Boolean = true

    private val features = listOf(
        "Points at this fork's own release feed, separate from upstream Mihon updates",
        "E-ink mode (Settings > Reader > General) - grayscale, no page-turn animations, tuned for devices like the Boox",
        "Lower CPU/heat during downloads by capping image-processing concurrency to the device's core count",
        "Per-source download concurrency overrides for sources that support many parallel chapters",
    )

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.themePadding.small),
        ) {
            Text(
                text = "This is TurboMihon, a personal fork of Mihon. It adds:",
                style = MaterialTheme.typography.bodyLarge,
            )
            features.forEach { feature ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TurboMihonStepPreview() {
    TachiyomiPreviewTheme {
        TurboMihonStep().Content()
    }
}
