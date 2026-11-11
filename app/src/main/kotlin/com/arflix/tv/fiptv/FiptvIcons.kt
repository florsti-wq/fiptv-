package com.arflix.tv.fiptv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * fIPTV: einheitliche Linien-Icons (eigene Zeichnungen, 24×24, runde Enden) – ersetzen Emojis und
 * gemischte Symbole in Menü, Sport und Funktionen. Farbe kommt über `tint` von Icon().
 */
object FiptvIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name = "fiptv.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply(block)
            .build()

    private fun ImageVector.Builder.stroked(d: String) {
        addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.path(fill: SolidColor, pathFillType: PathFillType, nodes: () -> List<androidx.compose.ui.graphics.vector.PathNode>) {
        addPath(pathData = nodes(), fill = fill, pathFillType = pathFillType)
    }

    val Search: ImageVector by lazy {
        icon("Search") {
        stroked("M4.0,11.0a7.0,7.0 0 1,0 14.0,0a7.0,7.0 0 1,0 -14.0,0Z")
        stroked("M20 20l-4-4")
        }
    }

    val Home: ImageVector by lazy {
        icon("Home") {
        stroked("M3 11l9-7 9 7")
        stroked("M5 10v10h14V10")
        stroked("M10 20v-6h4v6")
        }
    }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
        stroked("M6 3h12v18l-6-4-6 4z")
        }
    }

    val LiveTv: ImageVector by lazy {
        icon("LiveTv") {
        stroked("M5.0,7.0h14.0a2.0,2.0 0 0 1 2.0,2.0v9.0a2.0,2.0 0 0 1 -2.0,2.0h-14.0a2.0,2.0 0 0 1 -2.0,-2.0v-9.0a2.0,2.0 0 0 1 2.0,-2.0Z")
        stroked("M8 2.5l4 4.5 4-4.5")
        }
    }

    val Film: ImageVector by lazy {
        icon("Film") {
        stroked("M5.0,4.0h14.0a2.0,2.0 0 0 1 2.0,2.0v12.0a2.0,2.0 0 0 1 -2.0,2.0h-14.0a2.0,2.0 0 0 1 -2.0,-2.0v-12.0a2.0,2.0 0 0 1 2.0,-2.0Z")
        stroked("M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4")
        }
    }

    val Trophy: ImageVector by lazy {
        icon("Trophy") {
        stroked("M8 4h8v5a4 4 0 0 1-8 0z")
        stroked("M8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4")
        stroked("M12 13v4M8 21h8M9 17h6")
        }
    }

    val Mediathek: ImageVector by lazy {
        icon("Mediathek") {
        stroked("M3 9l9-5 9 5")
        stroked("M5 9v9M9.5 9v9M14.5 9v9M19 9v9")
        stroked("M3 20h18")
        }
    }

    val VideoScreen: ImageVector by lazy {
        icon("VideoScreen") {
        stroked("M5.0,4.0h14.0a2.0,2.0 0 0 1 2.0,2.0v9.0a2.0,2.0 0 0 1 -2.0,2.0h-14.0a2.0,2.0 0 0 1 -2.0,-2.0v-9.0a2.0,2.0 0 0 1 2.0,-2.0Z")
        stroked("M10 8.5v5l4-2.5z")
        stroked("M8 21h8M12 17v4")
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
        stroked("M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12")
        stroked("M14.0,6.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0Z")
        stroked("M8.0,12.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0Z")
        stroked("M16.0,18.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0Z")
        }
    }

    val Profile: ImageVector by lazy {
        icon("Profile") {
        stroked("M8.0,8.0a4.0,4.0 0 1,0 8.0,0a4.0,4.0 0 1,0 -8.0,0Z")
        stroked("M4 21a8 8 0 0 1 16 0")
        }
    }

    val Football: ImageVector by lazy {
        icon("Football") {
        stroked("M3.0,12.0a9.0,9.0 0 1,0 18.0,0a9.0,9.0 0 1,0 -18.0,0Z")
        stroked("M12 7.5l3.8 2.8-1.4 4.4H9.6l-1.4-4.4z")
        stroked("M12 7.5V3.2M15.8 10.3l4-1.2M14.4 14.7l2.4 3.4M9.6 14.7l-2.4 3.4M8.2 10.3l-4-1.2")
        }
    }

    val Formula1: ImageVector by lazy {
        icon("Formula1") {
        stroked("M5 21V4")
        stroked("M5 4h14v10H5")
        stroked("M9.7 4v10M14.3 4v10M5 9h14")
        }
    }

    val Tennis: ImageVector by lazy {
        icon("Tennis") {
        stroked("M3.0,12.0a9.0,9.0 0 1,0 18.0,0a9.0,9.0 0 1,0 -18.0,0Z")
        stroked("M5.6 5.6a9 9 0 0 1 0 12.8M18.4 5.6a9 9 0 0 0 0 12.8")
        }
    }

    val Wrestling: ImageVector by lazy {
        icon("Wrestling") {
        stroked("M2 10h20v4H2z")
        stroked("M7.5,12.0a4.5,4.5 0 1,0 9.0,0a4.5,4.5 0 1,0 -9.0,0Z")
        stroked("M10.5 12h3M12 10.5v3")
        }
    }

    val Overview: ImageVector by lazy {
        icon("Overview") {
        stroked("M4.5,3.0h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5Z")
        stroked("M15.5,3.0h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5Z")
        stroked("M4.5,14.0h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5Z")
        stroked("M15.5,14.0h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5Z")
        }
    }

    val Star: ImageVector by lazy {
        icon("Star") {
        stroked("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
        }
    }

    val OnTv: ImageVector by lazy {
        icon("OnTv") {
        stroked("M10.0,12.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0Z")
        stroked("M8.5 8.5a5 5 0 0 0 0 7M15.5 8.5a5 5 0 0 1 0 7M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8")
        }
    }

    val Bell: ImageVector by lazy {
        icon("Bell") {
        stroked("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z")
        stroked("M10 21h4")
        }
    }

    val Resume: ImageVector by lazy {
        icon("Resume") {
        stroked("M3 12a9 9 0 1 0 3-6.7")
        stroked("M3 4v4h4")
        stroked("M12 7v5l3 2")
        }
    }

    val NextEpisode: ImageVector by lazy {
        icon("NextEpisode") {
        stroked("M5 5l10 7-10 7z")
        stroked("M19 5v14")
        }
    }

    val PlayCircle: ImageVector by lazy {
        icon("PlayCircle") {
        stroked("M3.0,12.0a9.0,9.0 0 1,0 18.0,0a9.0,9.0 0 1,0 -18.0,0Z")
        stroked("M10 8.5v7l6-3.5z")
        }
    }

    val Goal: ImageVector by lazy {
        icon("Goal") {
        stroked("M3 19V7h18v12")
        stroked("M3 11h18M3 15h18M8 7v12M12 7v12M16 7v12")
        }
    }

    val Live: ImageVector by lazy {
        icon("Live") {
        stroked("M4.0,12.0a8.0,8.0 0 1,0 16.0,0a8.0,8.0 0 1,0 -16.0,0Z")
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) { addPathNodes("M8.5,12.0a3.5,3.5 0 1,0 7.0,0a3.5,3.5 0 1,0 -7.0,0Z") }
        }
    }

    val Kids: ImageVector by lazy {
        icon("Kids") {
        stroked("M12 3a6 6 0 0 1 6 6c0 4-3 7-6 7s-6-3-6-7a6 6 0 0 1 6-6z")
        stroked("M11 16h2l-1 2z")
        stroked("M12 18c0 2-2 1.5-2 3.5")
        }
    }

    val Stats: ImageVector by lazy {
        icon("Stats") {
        stroked("M4 20h16")
        stroked("M7 16v-5M12 16V6M17 16V9")
        }
    }

    val Table: ImageVector by lazy {
        icon("Table") {
        stroked("M9 6h11M9 12h11M9 18h11")
        stroked("M4 6h1M4 12h1M4 18h1")
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
        stroked("M5.0,5.0h14.0a2.0,2.0 0 0 1 2.0,2.0v12.0a2.0,2.0 0 0 1 -2.0,2.0h-14.0a2.0,2.0 0 0 1 -2.0,-2.0v-12.0a2.0,2.0 0 0 1 2.0,-2.0Z")
        stroked("M3 10h18M8 3v4M16 3v4")
        }
    }
}
