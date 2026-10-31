package com.mucage.rmb.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** 分类图标标识 → 图标，数据表仅存字符串标识 */
fun categoryIcon(icon: String): ImageVector = when (icon) {
    "restaurant" -> Icons.Outlined.Restaurant
    "snack" -> Icons.Outlined.Icecream
    "clothes" -> Icons.Outlined.Checkroom
    "entertainment" -> Icons.Outlined.Movie
    "education" -> Icons.Outlined.School
    "transport" -> Icons.Outlined.DirectionsBus
    "home" -> Icons.Outlined.Home
    "medical" -> Icons.Outlined.LocalHospital
    "telecom" -> Icons.Outlined.Smartphone
    "shopping" -> Icons.Outlined.ShoppingCart
    "travel" -> Icons.Outlined.Flight
    "pets" -> Icons.Outlined.Pets
    "sports" -> Icons.Outlined.FitnessCenter
    "beauty" -> Icons.Outlined.Spa
    "gift" -> Icons.Outlined.CardGiftcard
    "salary" -> Icons.Outlined.Work
    "bonus" -> Icons.Outlined.EmojiEvents
    "finance" -> Icons.Outlined.TrendingUp
    "parttime" -> Icons.Outlined.Luggage
    "redpacket" -> Icons.Outlined.Redeem
    else -> Icons.Outlined.Category
}

/** 自定义分类时可选的图标标识 */
val availableCategoryIcons: List<String> = listOf(
    "restaurant", "snack", "clothes", "entertainment", "education",
    "transport", "home", "medical", "telecom", "shopping",
    "travel", "pets", "sports", "beauty", "gift",
    "more", "salary", "bonus", "finance", "parttime", "redpacket"
)
