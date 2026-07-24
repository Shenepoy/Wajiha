package com.wajiha.data.prefs

import kotlinx.serialization.Serializable

enum class HeroDisplaySlot {
    Primary,
    Secondary,
}

@Serializable
enum class HeroElementId {
    Backdrop,
    Cover,
    Logo,
    PlatformIcon,
    Platform,
    Title,
    Metadata,
    Description,
    PlayStats,
    Favorite,
    SectionHint,
    ;

    val detailOnly: Boolean
        get() = this == Favorite || this == SectionHint
}

@Serializable
data class HeroElementLayout(
    val id: HeroElementId,
    val visible: Boolean = true,
    /** 0..1 from layout start edge (mirrored under RTL at resolve). */
    val x: Float = 0f,
    val y: Float = 0f,
    /** 0..1 width; 0 = intrinsic. */
    val w: Float = 0f,
    /** 0..1 height; 0 = intrinsic. */
    val h: Float = 0f,
)

@Serializable
data class HeroLayout(
    val presetId: String = HeroLayoutPresets.CLASSIC,
    val elements: List<HeroElementLayout> = emptyList(),
    val coverBorder: Boolean = false,
    val configured: Boolean = false,
) {
    fun element(id: HeroElementId): HeroElementLayout? = elements.firstOrNull { it.id == id }

    fun withElement(updated: HeroElementLayout): HeroLayout {
        val next =
            elements.map { if (it.id == updated.id) updated else it }.let { list ->
                if (list.any { it.id == updated.id }) list else list + updated
            }
        return copy(
            elements = next,
            presetId = HeroLayoutPresets.CUSTOM,
            configured = true,
        )
    }
}

@Serializable
data class HeroLayoutBundle(
    val primary: HeroLayout = HeroLayoutPresets.classic(configured = false),
    val secondary: HeroLayout = HeroLayoutPresets.classic(configured = false),
) {
    fun slot(slot: HeroDisplaySlot): HeroLayout =
        when (slot) {
            HeroDisplaySlot.Primary -> primary
            HeroDisplaySlot.Secondary -> secondary
        }

    fun withSlot(
        slot: HeroDisplaySlot,
        layout: HeroLayout,
    ): HeroLayoutBundle =
        when (slot) {
            HeroDisplaySlot.Primary -> copy(primary = layout)
            HeroDisplaySlot.Secondary -> copy(secondary = layout)
        }
}

object HeroLayoutPresets {
    const val CLASSIC = "classic"
    const val COVER_FOCUS = "cover_focus"
    const val LOGO_FOCUS = "logo_focus"
    const val MINIMAL = "minimal"
    const val TEXT_ONLY = "text_only"
    const val EMPTY = "empty"
    const val CUSTOM = "custom"

    val namedIds =
        listOf(
            CLASSIC,
            COVER_FOCUS,
            LOGO_FOCUS,
            MINIMAL,
            TEXT_ONLY,
            EMPTY,
        )

    fun label(presetId: String): String =
        when (presetId) {
            CLASSIC -> "Classic"
            COVER_FOCUS -> "Cover focus"
            LOGO_FOCUS -> "Logo focus"
            MINIMAL -> "Minimal"
            TEXT_ONLY -> "Text only"
            EMPTY -> "Empty"
            CUSTOM -> "Custom"
            else -> "Classic"
        }

    fun template(presetId: String): HeroLayout =
        when (presetId) {
            COVER_FOCUS -> coverFocus()
            LOGO_FOCUS -> logoFocus()
            MINIMAL -> minimal()
            TEXT_ONLY -> textOnly()
            EMPTY -> empty()
            CUSTOM -> classic(configured = true).copy(presetId = CUSTOM)
            else -> classic()
        }

    fun classic(
        configured: Boolean = false,
        coverBorder: Boolean = false,
        visibility: Map<HeroElementId, Boolean> = emptyMap(),
    ): HeroLayout {
        fun vis(
            id: HeroElementId,
            default: Boolean = true,
        ) = visibility[id] ?: default

        return HeroLayout(
            presetId = CLASSIC,
            coverBorder = coverBorder,
            configured = configured,
            elements =
                listOf(
                    HeroElementLayout(HeroElementId.Backdrop, vis(HeroElementId.Backdrop), 0f, 0f, 1f, 1f),
                    HeroElementLayout(HeroElementId.Cover, vis(HeroElementId.Cover), 0.04f, 0.08f, 0.28f, 0.84f),
                    HeroElementLayout(HeroElementId.Logo, vis(HeroElementId.Logo), 0.06f, 0.72f, 0.24f, 0.14f),
                    HeroElementLayout(HeroElementId.PlatformIcon, vis(HeroElementId.PlatformIcon), 0.36f, 0.28f, 0.06f, 0.08f),
                    HeroElementLayout(HeroElementId.Platform, vis(HeroElementId.Platform), 0.44f, 0.30f, 0.40f, 0.06f),
                    HeroElementLayout(HeroElementId.Title, vis(HeroElementId.Title), 0.36f, 0.38f, 0.56f, 0.12f),
                    HeroElementLayout(HeroElementId.Metadata, vis(HeroElementId.Metadata), 0.36f, 0.52f, 0.56f, 0.06f),
                    HeroElementLayout(HeroElementId.Description, vis(HeroElementId.Description), 0.36f, 0.60f, 0.56f, 0.18f),
                    HeroElementLayout(HeroElementId.PlayStats, vis(HeroElementId.PlayStats), 0.36f, 0.80f, 0.40f, 0.06f),
                    HeroElementLayout(HeroElementId.Favorite, vis(HeroElementId.Favorite, true), 0.36f, 0.22f, 0.20f, 0.06f),
                    HeroElementLayout(HeroElementId.SectionHint, vis(HeroElementId.SectionHint, false), 0.36f, 0.88f, 0.56f, 0.08f),
                ),
        )
    }

    fun coverFocus(): HeroLayout =
        HeroLayout(
            presetId = COVER_FOCUS,
            elements =
                listOf(
                    HeroElementLayout(HeroElementId.Backdrop, true, 0f, 0f, 1f, 1f),
                    HeroElementLayout(HeroElementId.Cover, true, 0.28f, 0.08f, 0.44f, 0.72f),
                    HeroElementLayout(HeroElementId.Logo, false, 0.32f, 0.62f, 0.36f, 0.12f),
                    HeroElementLayout(HeroElementId.PlatformIcon, true, 0.06f, 0.78f, 0.06f, 0.08f),
                    HeroElementLayout(HeroElementId.Platform, true, 0.14f, 0.80f, 0.30f, 0.06f),
                    HeroElementLayout(HeroElementId.Title, true, 0.06f, 0.86f, 0.55f, 0.10f),
                    HeroElementLayout(HeroElementId.Metadata, true, 0.62f, 0.88f, 0.32f, 0.06f),
                    HeroElementLayout(HeroElementId.Description, false, 0.06f, 0.70f, 0.40f, 0.12f),
                    HeroElementLayout(HeroElementId.PlayStats, false, 0.62f, 0.80f, 0.30f, 0.06f),
                    HeroElementLayout(HeroElementId.Favorite, true, 0.06f, 0.72f, 0.18f, 0.06f),
                    HeroElementLayout(HeroElementId.SectionHint, false, 0.06f, 0.92f, 0.50f, 0.06f),
                ),
        )

    fun logoFocus(): HeroLayout =
        HeroLayout(
            presetId = LOGO_FOCUS,
            elements =
                listOf(
                    HeroElementLayout(HeroElementId.Backdrop, true, 0f, 0f, 1f, 1f),
                    HeroElementLayout(HeroElementId.Cover, true, 0.04f, 0.08f, 0.16f, 0.28f),
                    HeroElementLayout(HeroElementId.Logo, true, 0.22f, 0.28f, 0.56f, 0.28f),
                    HeroElementLayout(HeroElementId.PlatformIcon, true, 0.22f, 0.62f, 0.06f, 0.08f),
                    HeroElementLayout(HeroElementId.Platform, true, 0.30f, 0.64f, 0.30f, 0.06f),
                    HeroElementLayout(HeroElementId.Title, true, 0.22f, 0.72f, 0.56f, 0.10f),
                    HeroElementLayout(HeroElementId.Metadata, false, 0.22f, 0.84f, 0.50f, 0.06f),
                    HeroElementLayout(HeroElementId.Description, false, 0.22f, 0.84f, 0.50f, 0.10f),
                    HeroElementLayout(HeroElementId.PlayStats, false, 0.22f, 0.90f, 0.30f, 0.06f),
                    HeroElementLayout(HeroElementId.Favorite, true, 0.04f, 0.40f, 0.16f, 0.06f),
                    HeroElementLayout(HeroElementId.SectionHint, false, 0.22f, 0.90f, 0.50f, 0.06f),
                ),
        )

    fun minimal(): HeroLayout =
        HeroLayout(
            presetId = MINIMAL,
            elements =
                listOf(
                    HeroElementLayout(HeroElementId.Backdrop, true, 0f, 0f, 1f, 1f),
                    HeroElementLayout(HeroElementId.Cover, false, 0.04f, 0.08f, 0.28f, 0.84f),
                    HeroElementLayout(HeroElementId.Logo, false, 0.06f, 0.72f, 0.24f, 0.14f),
                    HeroElementLayout(HeroElementId.PlatformIcon, true, 0.06f, 0.72f, 0.06f, 0.08f),
                    HeroElementLayout(HeroElementId.Platform, true, 0.14f, 0.74f, 0.40f, 0.06f),
                    HeroElementLayout(HeroElementId.Title, true, 0.06f, 0.82f, 0.70f, 0.12f),
                    HeroElementLayout(HeroElementId.Metadata, false, 0.06f, 0.92f, 0.50f, 0.06f),
                    HeroElementLayout(HeroElementId.Description, false, 0.06f, 0.70f, 0.50f, 0.12f),
                    HeroElementLayout(HeroElementId.PlayStats, false, 0.06f, 0.92f, 0.30f, 0.06f),
                    HeroElementLayout(HeroElementId.Favorite, false, 0.06f, 0.66f, 0.18f, 0.06f),
                    HeroElementLayout(HeroElementId.SectionHint, false, 0.06f, 0.92f, 0.50f, 0.06f),
                ),
        )

    fun textOnly(): HeroLayout =
        HeroLayout(
            presetId = TEXT_ONLY,
            elements =
                listOf(
                    HeroElementLayout(HeroElementId.Backdrop, true, 0f, 0f, 1f, 1f),
                    HeroElementLayout(HeroElementId.Cover, false, 0.04f, 0.08f, 0.28f, 0.84f),
                    HeroElementLayout(HeroElementId.Logo, false, 0.06f, 0.72f, 0.24f, 0.14f),
                    HeroElementLayout(HeroElementId.PlatformIcon, true, 0.08f, 0.22f, 0.06f, 0.08f),
                    HeroElementLayout(HeroElementId.Platform, true, 0.16f, 0.24f, 0.40f, 0.06f),
                    HeroElementLayout(HeroElementId.Title, true, 0.08f, 0.34f, 0.80f, 0.12f),
                    HeroElementLayout(HeroElementId.Metadata, true, 0.08f, 0.48f, 0.70f, 0.06f),
                    HeroElementLayout(HeroElementId.Description, true, 0.08f, 0.56f, 0.80f, 0.22f),
                    HeroElementLayout(HeroElementId.PlayStats, true, 0.08f, 0.80f, 0.40f, 0.06f),
                    HeroElementLayout(HeroElementId.Favorite, true, 0.08f, 0.16f, 0.20f, 0.06f),
                    HeroElementLayout(HeroElementId.SectionHint, false, 0.08f, 0.88f, 0.70f, 0.08f),
                ),
        )

    fun empty(): HeroLayout =
        HeroLayout(
            presetId = EMPTY,
            elements =
                HeroElementId.entries.map {
                    HeroElementLayout(it, visible = false, x = 0f, y = 0f, w = 0f, h = 0f)
                },
        )

    fun fromLegacyTopHero(settings: AppSettings): HeroLayoutBundle {
        val visibility =
            mapOf(
                HeroElementId.Backdrop to settings.topHeroBackdrop,
                HeroElementId.Cover to settings.topHeroCover,
                HeroElementId.Logo to settings.topHeroLogo,
                HeroElementId.PlatformIcon to settings.topHeroPlatformIcon,
                HeroElementId.Platform to settings.topHeroPlatform,
                HeroElementId.Title to settings.topHeroTitle,
                HeroElementId.Metadata to settings.topHeroMetadata,
                HeroElementId.Description to settings.topHeroDescription,
                HeroElementId.PlayStats to settings.topHeroPlayStats,
                HeroElementId.Favorite to settings.topHeroFavorite,
                HeroElementId.SectionHint to settings.topHeroSectionHint,
            )
        val classic =
            classic(
                configured = false,
                coverBorder = settings.topHeroCoverBorder,
                visibility = visibility,
            )
        return HeroLayoutBundle(primary = classic, secondary = classic)
    }
}
