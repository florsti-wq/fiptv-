package com.arflix.tv.ui.components

import androidx.compose.ui.graphics.Color

/**
 * fIPTV: animierte Profilbilder – Microsoft Fluent Emoji Animated (MIT-Lizenz,
 * https://github.com/microsoft/fluentui-emoji-animated). Dateien in assets/avatars/
 * (<slug>.webp animiert, <slug>_still.webp unbewegt), erzeugt vom Workflow fiptv-avatars.yml.
 * IDs ab 101; ältere IDs (1–84) mit passendem Motiv werden auf die animierte Version abgebildet.
 */
object FiptvAnimatedAvatars {
    const val FIRST_ID = 101

    val slugs: List<String> = listOf(
        "cat_face",
        "dog_face",
        "fox",
        "owl",
        "penguin",
        "panda",
        "bear",
        "polar_bear",
        "rabbit_face",
        "hamster",
        "mouse_face",
        "frog",
        "monkey_face",
        "lion",
        "tiger_face",
        "koala",
        "raccoon",
        "hedgehog",
        "sloth",
        "otter",
        "wolf",
        "pig_face",
        "cow_face",
        "horse_face",
        "unicorn",
        "dragon_face",
        "t-rex",
        "dolphin",
        "octopus",
        "turtle",
        "butterfly",
        "parrot",
        "front-facing_baby_chick",
        "phoenix",
        "robot",
        "alien",
        "alien_monster",
        "ghost",
        "disguised_face",
        "skull",
        "smiling_face_with_sunglasses",
        "nerd_face",
        "clown_face",
        "cowboy_hat_face",
        "partying_face",
        "zany_face",
        "smiling_face_with_heart-eyes",
        "star-struck",
        "smiling_face_with_halo",
        "shushing_face",
        "smiling_face_with_horns",
        "ogre",
        "goblin",
        "cold_face",
        "exploding_head",
        "hugging_face",
        "face_with_monocle",
        "grinning_cat",
        "sleeping_face",
        "rocket",
        "flying_saucer",
        "fire",
        "rainbow",
        "snowman",
        "comet",
        "glowing_star",
        "ringed_planet",
        "sun_with_face",
        "full_moon_face",
        "jack-o-lantern",
        "teddy_bear",
        "crystal_ball",
        "party_popper",
        "birthday_cake",
        "hot_beverage",
        "musical_notes",
        "christmas_tree",
    )

    val categories: List<Pair<String, List<Int>>> = listOf(
        "Tiere" to listOf(101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134),
        "Figuren" to listOf(135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159),
        "Spaß" to listOf(160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177),
    )

    private val legacy: Map<Int, Int> = mapOf(
        1 to 101,
        2 to 102,
        3 to 103,
        4 to 104,
        5 to 105,
        6 to 106,
        7 to 135,
        8 to 136,
        9 to 138,
        10 to 139,
        11 to 140,
        12 to 137,
        16 to 160,
        17 to 166,
        18 to 162,
        21 to 125,
        22 to 126,
        23 to 127,
        25 to 107,
        26 to 109,
        27 to 110,
        28 to 112,
        29 to 113,
        30 to 114,
        31 to 115,
        32 to 116,
        33 to 111,
        34 to 128,
        35 to 129,
        36 to 131,
        37 to 130,
        38 to 117,
        39 to 118,
        40 to 141,
        41 to 142,
        42 to 143,
        43 to 144,
        44 to 145,
        45 to 146,
        46 to 147,
        47 to 148,
        48 to 149,
        49 to 150,
        50 to 170,
        51 to 152,
        52 to 153,
        53 to 154,
        54 to 155,
        60 to 172,
        70 to 163,
        79 to 165,
    )

    /** Animierte ID für eine Profilbild-ID (neu oder alt), sonst null. */
    fun resolve(avatarId: Int): Int? = when {
        avatarId >= FIRST_ID && avatarId - FIRST_ID < slugs.size -> avatarId
        else -> legacy[avatarId]
    }

    fun assetUri(id: Int, animated: Boolean): String {
        val slug = slugs[id - FIRST_ID]
        return "file:///android_asset/avatars/" + slug + (if (animated) ".webp" else "_still.webp")
    }

    fun gradient(id: Int): Pair<Color, Color> {
        val cat = categories.firstOrNull { id in it.second }?.first
        return when (cat) {
            "Tiere" -> Color(0xFF1E1A12) to Color(0xFF2E2618)
            "Figuren" -> Color(0xFF1A1630) to Color(0xFF28204A)
            "Spaß" -> Color(0xFF0E1A2A) to Color(0xFF16283E)
            else -> Color(0xFF1A1A1A) to Color(0xFF2D2D2D)
        }
    }
}
