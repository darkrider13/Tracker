package com.myapp.app.util

import androidx.compose.ui.graphics.Color
import com.myapp.app.ui.theme.Brand

/** One choice in "Choose Your Avatar" (AVATAR_OPTIONS in the old app). */
class AvatarOption(val key: String, val icon: String, val color: Color, val label: String)

val AVATAR_OPTIONS: List<AvatarOption> = listOf(
    AvatarOption("ninja", "fa-user-ninja", Color(0xFF4F46E5), "Ninja"),
    AvatarOption("astronaut", "fa-user-astronaut", Color(0xFF2563EB), "Astronaut"),
    AvatarOption("shield", "fa-user-shield", Color(0xFF0F766E), "Guardian"),
    AvatarOption("tie", "fa-user-tie", Color(0xFF0E7490), "Classic"),
    AvatarOption("robot", "fa-robot", Color(0xFF0D9488), "Robot"),
    AvatarOption("ghost", "fa-ghost", Color(0xFF7C3AED), "Ghost"),
    AvatarOption("cat", "fa-cat", Color(0xFFEA580C), "Cat"),
    AvatarOption("dragon", "fa-dragon", Brand.green, "Dragon"),
    AvatarOption("dress", "fa-person-dress", Color(0xFFDB2777), "Elegant"),
    AvatarOption("crown", "fa-crown", Color(0xFFCA8A04), "Royal"),
    AvatarOption("gem", "fa-gem", Color(0xFFEC4899), "Gem"),
    AvatarOption("spa", "fa-spa", Color(0xFFF472B6), "Blossom"),
    AvatarOption("heart", "fa-heart", Color(0xFFE11D48), "Sweetheart"),
    AvatarOption("glasses", "fa-glasses", Color(0xFFB45309), "Wise"),
    AvatarOption("book", "fa-book", Brand.green, "Scholar"),
    AvatarOption("tea", "fa-mug-hot", Color(0xFF78350F), "Cozy"),
    AvatarOption("chess", "fa-chess-king", Color(0xFF64748B), "Veteran"),
    AvatarOption("simple", "fa-user", Color(0xFF71717A), "Simple"),
)

/** getAvatarOption(key): unknown keys fall back to the last one ("simple"). */
fun getAvatarOption(key: String?): AvatarOption = AVATAR_OPTIONS.firstOrNull { it.key == key } ?: AVATAR_OPTIONS.last()
