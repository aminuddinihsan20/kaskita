package com.kaskita.lokal.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.kaskita.lokal.ui.theme.LocalDarkTheme
import com.kaskita.lokal.ui.theme.Motion

/** Shared backdrop for the frosted bars: scrolling content registers as a source, bars blur what is behind them. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Space a tab screen must leave at the bottom so its last item can scroll clear of the floating navigation bar. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

/**
 * Frosted glass: blurs and tints whatever scrolls behind. Below Android 12 (no blur effect) or without a registered source it
 * falls back to a nearly opaque tint, so text on the bar stays readable either way.
 */
@Composable
fun Modifier.frosted(): Modifier {
    val state = LocalHazeState.current
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val fallback = scheme.surfaceContainerLowest.copy(alpha = 0.94f)
    return if (state == null) {
        background(fallback)
    } else {
        hazeEffect(
            state = state,
            style = HazeStyle(
                backgroundColor = scheme.surface,
                tints = listOf(HazeTint(scheme.surfaceContainerLowest.copy(alpha = if (dark) 0.50f else 0.58f))),
                blurRadius = 28.dp,
                noiseFactor = 0.03f,
                fallbackTint = HazeTint(fallback),
            ),
        )
    }
}

data class NavEntry(val route: String, val label: String, val icon: ImageVector)

private val NavBarShape = RoundedCornerShape(36.dp)

/** A pill that floats above the content. The selected destination gets a pastel indicator that widens with a spring. */
@Composable
fun FloatingNavBar(entries: List<NavEntry>, selectedRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NavBarShape)
                .frosted()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), NavBarShape)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            entries.forEach { entry -> NavBarItem(entry, entry.route == selectedRoute) { onSelect(entry.route) } }
        }
    }
}

@Composable
private fun RowScope.NavBarItem(entry: NavEntry, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val indicator by animateColorAsState(if (selected) scheme.secondaryContainer else Color.Transparent, Motion.effects(), label = "navIndicator")
    val indicatorWidth by animateDpAsState(if (selected) 64.dp else 36.dp, Motion.spatial(), label = "navIndicatorWidth")
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(28.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(indicatorWidth).height(32.dp).clip(CircleShape).background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
        Text(
            entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Chrome for the four main tabs: a large title that collapses on scroll (frosted once collapsed) and a content area that
 * extends behind both the bar and the floating navigation. [content] gets the padding to apply to its scrolling list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = LocalHazeState.current
    val collapsed by remember { derivedStateOf { scroll.state.collapsedFraction > 0.5f } }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = NoInsets,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                expandedHeight = 100.dp,
                scrollBehavior = scroll,
                modifier = if (collapsed) Modifier.frosted() else Modifier,
            )
        },
        floatingActionButton = { Box(Modifier.padding(bottom = LocalFloatingBarInset.current)) { floatingActionButton() } },
    ) { inner ->
        Box(Modifier.fillMaxSize().then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)) {
            content(PaddingValues(top = inner.calculateTopPadding(), bottom = LocalFloatingBarInset.current))
        }
    }
}
