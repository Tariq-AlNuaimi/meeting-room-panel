package com.rihal.roompanel.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rihal.roompanel.R

@Composable
fun PairingScreen(state: PairingState, serverHost: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.pair_title),
            color = colors.onBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        when (state) {
            is PairingState.ShowingCode -> {
                Text(
                    state.code,
                    color = colors.onBackground,
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                )
                Text(
                    stringResource(R.string.pair_instructions),
                    color = colors.onSurfaceVariant,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 720.dp),
                )
                Text(stringResource(R.string.pair_expires), color = colors.onSurfaceVariant, fontSize = 18.sp)
            }
            PairingState.Connecting -> Text(stringResource(R.string.pair_connecting), color = colors.onSurfaceVariant, fontSize = 24.sp)
            PairingState.CantReachServer -> Text(stringResource(R.string.pair_error), color = colors.onSurfaceVariant, fontSize = 24.sp, textAlign = TextAlign.Center)
        }
        Text(stringResource(R.string.pair_server, serverHost), color = colors.onSurfaceVariant, fontSize = 14.sp)
    }
}
