package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Two-step dialog for [DownloadPreferences.sourceConcurrencyOverrides]: pick a source from the
 * list of currently loaded sources, then set its chapter/page concurrency overrides.
 */
@Composable
fun SourceConcurrencyOverrideDialog(
    sources: List<HttpSource>,
    overridesById: Map<Long, DownloadPreferences.SourceConcurrencyOverride>,
    onDismissRequest: () -> Unit,
    onSetOverride: (source: HttpSource, chapterConcurrency: Int, pageConcurrency: Int) -> Unit,
    onRemoveOverride: (source: HttpSource) -> Unit,
) {
    var selectedSource by remember { mutableStateOf<HttpSource?>(null) }

    val source = selectedSource
    if (source == null) {
        var query by remember { mutableStateOf("") }
        val filteredSources = remember(sources, query) {
            if (query.isBlank()) {
                sources
            } else {
                sources.filter { it.name.contains(query, ignoreCase = true) }
            }
        }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MR.strings.pref_download_source_concurrency_overrides)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(text = stringResource(MR.strings.action_search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    Box {
                        val listState = rememberLazyListState()
                        LazyColumn(state = listState) {
                            items(items = filteredSources, key = { it.id }) { item ->
                                val override = overridesById[item.id]
                                Row(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { selectedSource = item }
                                        .defaultMinSize(minHeight = 48.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(text = "${item.name} (${item.lang})")
                                        if (override != null) {
                                            val pagesLabel = if (override.pageConcurrency == 0) {
                                                stringResource(
                                                    MR.strings.pref_download_source_concurrency_pages_unlimited,
                                                )
                                            } else {
                                                override.pageConcurrency.toString()
                                            }
                                            Text(
                                                text = stringResource(
                                                    MR.strings.pref_download_source_concurrency_overrides_value,
                                                    override.chapterConcurrency,
                                                    pagesLabel,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (listState.canScrollBackward) {
                            HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                        }
                        if (listState.canScrollForward) {
                            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    } else {
        val existing = overridesById[source.id]
        var chapterConcurrency by remember { mutableIntStateOf(existing?.chapterConcurrency ?: 10) }
        var pageConcurrency by remember { mutableIntStateOf(existing?.pageConcurrency ?: 0) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = source.name) },
            text = {
                Column {
                    BaseSliderItem(
                        value = chapterConcurrency,
                        valueRange = 1..20,
                        title = stringResource(MR.strings.pref_download_concurrent_sources),
                        onChange = { chapterConcurrency = it },
                    )
                    BaseSliderItem(
                        value = pageConcurrency,
                        valueRange = 0..20,
                        valueString = if (pageConcurrency == 0) {
                            stringResource(MR.strings.pref_download_source_concurrency_pages_unlimited)
                        } else {
                            pageConcurrency.toString()
                        },
                        title = stringResource(MR.strings.pref_download_concurrent_pages),
                        subtitle = stringResource(MR.strings.pref_download_source_concurrency_pages_zero_hint),
                        onChange = { pageConcurrency = it },
                    )
                }
            },
            dismissButton = {
                if (existing != null) {
                    TextButton(
                        onClick = {
                            onRemoveOverride(source)
                            onDismissRequest()
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_remove))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetOverride(source, chapterConcurrency, pageConcurrency)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_save))
                }
            },
        )
    }
}
