package com.darcloud.omarai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darcloud.omarai.R

@Composable
fun OmarMark(
    modifier: Modifier = Modifier,
    size: Int = 44,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.omar_ai_icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * The Omar AI visual companion. This is intentionally a non-interactive brand
 * element; actions remain available through the clearly labelled controls.
 */
@Composable
fun OmarCompanion(
    modifier: Modifier = Modifier,
    size: Int = 96,
) {
    Image(
        painter = painterResource(R.drawable.omar_ai_companion),
        contentDescription = stringResource(R.string.omar_ai_companion_description),
        modifier = modifier.size(size.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun PageTitle(title: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HonestInfo(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.secondary)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    val label = text.replace('_', ' ')
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val foreground = accessibleStatusColor(color, darkTheme)
    Surface(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Status: $label"
        },
        color = foreground.copy(alpha = if (darkTheme) 0.10f else 0.12f),
        shape = CircleShape,
    ) {
        Text(
            label,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun accessibleStatusColor(color: Color, darkTheme: Boolean): Color {
    if (darkTheme) return color
    return when (color) {
        OmarTeal -> Color(0xFF006B60)
        OmarAmber -> Color(0xFF755000)
        OmarRed -> Color(0xFFBA1A1A)
        OmarBlue -> Color(0xFF2859A5)
        else -> color
    }
}
