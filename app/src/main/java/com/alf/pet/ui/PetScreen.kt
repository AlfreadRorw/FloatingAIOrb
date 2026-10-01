package com.alf.pet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alf.pet.PetRules
import com.alf.pet.PetState
import com.alf.pet.PetViewModel
import com.alf.pet.R
import kotlinx.coroutines.delay

private val IDLE_FRAMES = intArrayOf(
    R.drawable.idle_01,
    R.drawable.idle_02,
    R.drawable.idle_03,
    R.drawable.idle_04,
    R.drawable.idle_05,
    R.drawable.idle_06,
    R.drawable.idle_07,
    R.drawable.idle_08
)

private val HungerColor = Color(0xFFF59E0B)
private val HappinessColor = Color(0xFFEC4899)
private val EnergyColor = Color(0xFF3B82F6)
private val CleanColor = Color(0xFF14B8A6)
private val CoinColor = Color(0xFFF59E0B)

@Composable
fun PetScreen(viewModel: PetViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(state)

            PetCard(
                state = state,
                message = message,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            StatsCard(state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    label = "FEED",
                    icon = Icons.Filled.Restaurant,
                    enabled = PetRules.canFeed(state),
                    onClick = viewModel::feed,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "PLAY",
                    icon = Icons.Filled.SportsEsports,
                    enabled = PetRules.canPlay(state),
                    onClick = viewModel::play,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "SLEEP",
                    icon = Icons.Filled.Bedtime,
                    enabled = PetRules.canSleep(state),
                    onClick = viewModel::sleep,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    label = "CLEAN",
                    icon = Icons.Filled.CleaningServices,
                    enabled = PetRules.canClean(state),
                    onClick = viewModel::clean,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun panelColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface
)

@Composable
private fun Header(state: PetState) {
    val needed = PetRules.xpToNext(state.level)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = panelColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Level ${state.level}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Filled.MonetizationOn,
                    contentDescription = "Coins",
                    tint = CoinColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${state.coins}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "XP",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { state.xp / needed.toFloat() },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${state.xp} / $needed XP",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PetCard(state: PetState, message: String?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = panelColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IdlePet(
                frameDelayMs = if (state.energy < 20f) 220L else 120L,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Text(
                text = "Mood: ${PetRules.moodLabel(state)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun IdlePet(frameDelayMs: Long, modifier: Modifier = Modifier) {
    val painters = IDLE_FRAMES.map { painterResource(id = it) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(frameDelayMs) {
        while (true) {
            delay(frameDelayMs)
            frame = (frame + 1) % IDLE_FRAMES.size
        }
    }

    Image(
        painter = painters[frame],
        contentDescription = "ALF PET",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun StatsCard(state: PetState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = panelColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatRow("Hunger", Icons.Filled.Restaurant, state.hunger, HungerColor)
            StatRow("Happiness", Icons.Filled.Favorite, state.happiness, HappinessColor)
            StatRow("Energy", Icons.Filled.Bolt, state.energy, EnergyColor)
            StatRow("Cleanliness", Icons.Filled.WaterDrop, state.cleanliness, CleanColor)
        }
    }
}

@Composable
private fun StatRow(label: String, icon: ImageVector, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(92.dp)
        )
        LinearProgressIndicator(
            progress = { value / PetRules.MAX_STAT },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${value.toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}
