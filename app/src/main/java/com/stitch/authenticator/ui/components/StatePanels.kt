package com.stitch.authenticator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stitch.authenticator.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularWavyProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.loading_accounts),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    StatePanel(
        icon = { Icon(Icons.Rounded.Key, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
        title = stringResource(R.string.no_accounts_title),
        body = stringResource(R.string.no_accounts_body),
        modifier = modifier,
    )
}

@Composable
fun NoResultsState(modifier: Modifier = Modifier) {
    StatePanel(
        icon = { Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
        title = stringResource(R.string.no_results_title),
        body = stringResource(R.string.no_results_body),
        modifier = modifier,
    )
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun StatePanel(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
