package com.yokonex.bililive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yokonex.bililive.app.navigation.NavigationItemSpec

@Composable
internal fun WorkspaceShell(
    items: List<NavigationItemSpec>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF150A1B),
                        Color(0xFF100913),
                        Color(0xFF08060A),
                    ),
                ),
            ),
    ) {
        val sidebarMode = maxWidth >= 900.dp
        if (sidebarMode) {
            Row(modifier = Modifier.fillMaxSize()) {
                WorkspaceSidebar(
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    content(PaddingValues(horizontal = 24.dp, vertical = 20.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkspaceSidebar(
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    content(PaddingValues(horizontal = 12.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
fun WorkspacePageHeader(
    title: String,
    statusLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 520.dp
        if (compact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!statusLabel.isNullOrBlank()) {
                    StatusPill(label = statusLabel)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (!statusLabel.isNullOrBlank()) {
                    StatusPill(label = statusLabel)
                }
            }
        }
    }
}

@Composable
fun WorkspaceSectionHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun WorkspaceMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    WorkspaceCard(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun WorkspaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF2160D1C),
            // 工作台卡片统一使用浅色前景，避免默认内容色回退成黑字。
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, Color(0x33D98AA8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 420.dp
            Column(
                modifier = Modifier.padding(if (compact) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0x26D98AA8),
        border = BorderStroke(1.dp, Color(0x55D98AA8)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun WorkspaceSidebar(
    items: List<NavigationItemSpec>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    Column(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF21D1019),
                        Color(0xFA0C080B),
                    ),
                ),
            )
            .border(1.dp, Color(0x33D98AA8))
            .then(if (compact) Modifier else Modifier.verticalScroll(verticalScrollState))
            .padding(
                horizontal = if (compact) 12.dp else 18.dp,
                vertical = if (compact) 14.dp else 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
    ) {
        WorkspaceSidebarBrand(compact = compact)
        if (compact) {
            // 手机端改成顶部工作台，避免桌面侧栏在竖屏里挤占主体内容高度。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    WorkspaceSidebarNavItem(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        compact = true,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    WorkspaceSidebarNavItem(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSidebarBrand(compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp))
            .padding(if (compact) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 42.dp else 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE0A1B9),
                            Color(0xFFBA6887),
                        ),
                    ),
                )
                .padding(if (compact) 9.dp else 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = Color(0xFF160D1C),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "BILIBILI LIVE SIGNAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "BiliLive YOKONEX",
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "直播互动控制台",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkspaceSidebarNavItem(
    item: NavigationItemSpec,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = if (selected) {
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFD98AA8), Color(0xFFBA6887)),
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(Color(0x0DFFFFFF), Color(0x08FFFFFF)),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) Color(0x66FFF0F5) else Color(0x14FFFFFF),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 10.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = item.iconKey.toWorkspaceIcon(),
            contentDescription = null,
            tint = if (selected) Color(0xFFFFF8FB) else Color(0xFFFFD8E8),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color(0xFFFFF8FB) else Color(0xFFFFD8E8),
        )
    }
}

@Composable
fun workspaceFilledButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
    )

@Composable
fun workspaceOutlinedButtonColors() =
    ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
    )

@Composable
fun workspaceFilterChipColors() =
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = Color(0x0DFFFFFF),
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
fun workspaceOutlinedTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = Color(0x08FFFFFF),
        unfocusedContainerColor = Color(0x05FFFFFF),
        disabledContainerColor = Color(0x03FFFFFF),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
    )

@Composable
fun workspaceSwitchColors() =
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = Color(0x22FFFFFF),
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    )

private fun com.yokonex.bililive.app.navigation.NavigationIcon.toWorkspaceIcon(): ImageVector =
    when (this) {
        com.yokonex.bililive.app.navigation.NavigationIcon.Dashboard -> Icons.Filled.Dashboard
        com.yokonex.bililive.app.navigation.NavigationIcon.Events -> Icons.Filled.Tune
        com.yokonex.bililive.app.navigation.NavigationIcon.Waveforms -> Icons.Filled.GraphicEq
    }
