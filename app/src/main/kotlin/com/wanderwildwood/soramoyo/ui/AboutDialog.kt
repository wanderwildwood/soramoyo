package com.wanderwildwood.soramoyo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.soramoyo.BuildConfig
import com.wanderwildwood.soramoyo.R

/**
 * What this is, what it sends where, and whose work it is built on.
 *
 * The line about the position is here because a stranger cannot safely assume the answer: a
 * radar and a forecast both need to know roughly where the phone is, and they ask two
 * services this app does not run.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(
            text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(text = stringResource(R.string.about_privacy), style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(14.dp))
        TextMMD(text = stringResource(R.string.about_licence), style = MaterialTheme.typography.labelSmall)
        TextMMD(text = stringResource(R.string.about_after), style = MaterialTheme.typography.labelSmall)
        TextMMD(text = stringResource(R.string.about_data), style = MaterialTheme.typography.labelSmall)
        TextMMD(text = stringResource(R.string.about_icons), style = MaterialTheme.typography.labelSmall)

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
        )

        Spacer(Modifier.height(14.dp))
        Llama()

        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.about_close), style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * Straight to the checkout rather than the donation page on the site, which only leads there
 * anyway. A phone with nothing registered for a web address throws, and this says so out loud
 * rather than swallowing it and leaving a press that does nothing with no explanation.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    var dead by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                    )
                }.isSuccess
                dead = !opened
            }
            .padding(vertical = 4.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.llama),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        TextMMD(
            text = if (dead) {
                stringResource(R.string.about_no_browser, "square.link/u/AGu8oT10")
            } else {
                stringResource(R.string.about_feed_the_llamas)
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
