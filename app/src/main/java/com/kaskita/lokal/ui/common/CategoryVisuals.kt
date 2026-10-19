package com.kaskita.lokal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.ui.theme.CategoryTones
import com.kaskita.lokal.ui.theme.ExpressiveShapes
import com.kaskita.lokal.ui.theme.LocalDarkTheme

/** Icon and color keys are stored in the database as plain strings; this is the only place that maps them to visuals. */
object CategoryVisuals {
    val iconKeys: List<String> = listOf(
        "restaurant", "shopping_cart", "directions_car", "receipt", "home", "health", "school", "movie",
        "shopping_bag", "work", "payments", "storefront", "star", "card_giftcard", "undo", "savings", "more_horiz",
    )
    val colorKeys: List<String> = listOf("orange", "blue", "teal", "purple", "brown", "red", "indigo", "pink", "amber", "green", "gray")

    fun icon(key: String): ImageVector = when (key) {
        "restaurant" -> Icons.Filled.Restaurant
        "shopping_cart" -> Icons.Filled.ShoppingCart
        "directions_car" -> Icons.Filled.DirectionsCar
        "receipt" -> Icons.Filled.Receipt
        "home" -> Icons.Filled.Home
        "health" -> Icons.Filled.HealthAndSafety
        "school" -> Icons.Filled.School
        "movie" -> Icons.Filled.Movie
        "shopping_bag" -> Icons.Filled.ShoppingBag
        "work" -> Icons.Filled.Work
        "payments" -> Icons.Filled.Payments
        "storefront" -> Icons.Filled.Storefront
        "star" -> Icons.Filled.Star
        "card_giftcard", "gift" -> Icons.Filled.CardGiftcard
        "undo" -> Icons.AutoMirrored.Filled.Undo
        "savings" -> Icons.Filled.Savings
        "more_horiz" -> Icons.Filled.MoreHoriz
        else -> Icons.Filled.Category
    }

    private fun toneOf(colorKey: String) = CategoryTones[colorKey] ?: CategoryTones.getValue("gray")

    /** Icon tint: dark enough to read on [container]. */
    @Composable
    fun tint(colorKey: String): Color = toneOf(colorKey).let { if (LocalDarkTheme.current) it.darkIcon else it.lightIcon }

    /** The pastel fill behind an icon. */
    @Composable
    fun container(colorKey: String): Color = toneOf(colorKey).let { if (LocalDarkTheme.current) it.darkContainer else it.lightContainer }

    /** Mid-tone fill for chart segments (at least 3:1 against the card). */
    @Composable
    fun chart(colorKey: String): Color = toneOf(colorKey).let { if (LocalDarkTheme.current) it.darkChart else it.lightChart }
}

/** An icon on a pastel organic shape. The silhouette (from [shapeKey]) is part of what identifies the thing it stands for. */
@Composable
fun IconBadge(icon: ImageVector, container: Color, tint: Color, shapeKey: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier.size(size).clip(ExpressiveShapes.forKey(shapeKey)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** A category's own icon, pastel color and shape. */
@Composable
fun CategoryBadge(iconKey: String, colorKey: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    IconBadge(CategoryVisuals.icon(iconKey), CategoryVisuals.container(colorKey), CategoryVisuals.tint(colorKey), iconKey, modifier, size)
}
