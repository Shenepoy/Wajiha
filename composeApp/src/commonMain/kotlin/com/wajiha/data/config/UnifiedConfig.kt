package com.wajiha.data.config

import kotlinx.serialization.Serializable

/**
 * Wajiha's internal platform/emulator config schema. Daijishō and iiSU
 * configs are converted into this shape by the importers; the bundled
 * starter set ships in this format directly.
 */
@Serializable
data class PlatformConfig(
    val id: String,
    val name: String,
    val shortName: String,
    /** ROM extensions without dot, lowercase */
    val extensions: List<String> = emptyList(),
    val raConsoleId: Int? = null,
    val screenScraperId: Int? = null,
    val libretroName: String? = null,
    val boxartAspectRatio: String? = null,
    val sortIndex: Int = 0,
    val emulators: List<EmulatorConfig> = emptyList(),
)

@Serializable
data class EmulatorConfig(
    val id: String,
    val name: String,
    /** Candidate packages in priority order; first installed wins */
    val packageNames: List<String> = emptyList(),
    val activityName: String? = null,
    val action: String? = null,
    /** "uri" | "path" */
    val routeType: String = "uri",
    /** Raw am-start arguments string (Daijishō / iiSU command) */
    val amStartArguments: String? = null,
    val extras: List<IntentExtra> = emptyList(),
    val activityFlags: List<String> = emptyList(),
    val keepSafUri: Boolean = false,
    val killBeforeLaunch: Boolean = false,
    val libretroCore: String? = null,
    val isDefault: Boolean = false,
)

@Serializable
data class IntentExtra(
    val key: String,
    val value: String,
    /** string | int | long | boolean | float */
    val type: String = "string",
)
