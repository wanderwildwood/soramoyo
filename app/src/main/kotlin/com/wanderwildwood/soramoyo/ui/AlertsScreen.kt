package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.alerts.Alert
import com.wanderwildwood.soramoyo.alerts.AlertNotices
import com.wanderwildwood.soramoyo.alerts.Alerts

/**
 * The line at the top of Today while an alert is in effect: the first to end, and until when,
 * in bold, the house's only emphasis. A press opens them all.
 */
@Composable
internal fun AlertLine(alerts: List<Alert>, onOpen: () -> Unit) {
    val first = alerts.firstOrNull() ?: return
    val context = LocalContext.current
    val open = stringResource(R.string.alerts_cd_open)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .semantics { contentDescription = open }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(
                text = first.until?.let { stringResource(R.string.alerts_line_until, first.event, AlertNotices.untilText(context, it)) }
                    ?: first.event,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextMMD(text = "›", style = MaterialTheme.typography.titleLarge)
        }
        if (alerts.size > 1) {
            TextMMD(
                text = pluralStringResource(R.plurals.alerts_line_more, alerts.size - 1, alerts.size - 1),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Every alert in effect, each as the Weather Service wrote it: its name, headline, the areas
 * it covers, from when until when, what it says and what to do. Each paragraph is a row of its
 * own, so the pages turn on whole paragraphs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(alerts: List<Alert>, onClose: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.alerts_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.sky_cd_back), onClose) },
            )
        },
    ) { contentPadding ->
        PagedColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp),
        ) {
            if (alerts.isEmpty()) {
                item { Paragraph(stringResource(R.string.alerts_none)) }
            }
            alerts.forEachIndexed { i, alert ->
                item {
                    if (i > 0) HorizontalDividerMMD()
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                        TextMMD(text = alert.event, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val onset = alert.onset
                        val until = alert.until
                        val times = when {
                            onset != null && until != null -> stringResource(
                                R.string.alerts_from_until,
                                AlertNotices.untilText(context, onset),
                                AlertNotices.untilText(context, until),
                            )
                            until != null -> stringResource(R.string.alerts_until, AlertNotices.untilText(context, until))
                            else -> null
                        }
                        times?.let { TextMMD(text = it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (alert.headline.isNotEmpty()) item { Small(alert.headline) }
                if (alert.areas.isNotEmpty()) item { Small(stringResource(R.string.alerts_areas, alert.areas)) }
                Alerts.paragraphs(alert.description).forEach { item { Paragraph(it) } }
                val todo = Alerts.paragraphs(alert.instruction)
                if (todo.isNotEmpty()) {
                    // The heading goes with its first paragraph, or it can end a page alone.
                    item {
                        Column {
                            TextMMD(
                                text = stringResource(R.string.alerts_what_to_do),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Paragraph(todo.first())
                        }
                    }
                    todo.drop(1).forEach { item { Paragraph(it) } }
                }
                if (alert.sender.isNotEmpty()) item { Small(alert.sender) }
            }
            item {
                Column {
                    HorizontalDividerMMD()
                    Spacer(Modifier.height(10.dp))
                    TextMMD(text = stringResource(R.string.alerts_coverage), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun Small(text: String) {
    TextMMD(text = text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 4.dp))
}
