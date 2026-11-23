package com.efprojects.vombatsu_client.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.feed.userUrl
import com.efprojects.vombatsu_client.post.BackButton

private const val AUTHOR_LOGIN = "Egor3f"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.about_unofficial), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.bodyLarge)
                val linkStyle = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
                val prefix = stringResource(R.string.about_tester)
                Text(
                    buildAnnotatedString {
                        append(prefix)
                        append(' ')
                        withLink(LinkAnnotation.Url(userUrl(AUTHOR_LOGIN), TextLinkStyles(linkStyle))) {
                            append(AUTHOR_LOGIN)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(stringResource(R.string.about_bug_icon), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
