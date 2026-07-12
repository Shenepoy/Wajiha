package com.wajiha.data.ra

import com.wajiha.data.WajihaJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RaUserProfile(
    @SerialName("User") val user: String = "",
    @SerialName("TotalPoints") val totalPoints: Int = 0,
    @SerialName("TotalTruePoints") val totalTruePoints: Int = 0,
    @SerialName("UserPic") val userPic: String? = null,
)

@Serializable
data class RaAchievement(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeName") val badgeName: String = "",
    @SerialName("DisplayOrder") val displayOrder: Int = 0,
    @SerialName("DateEarned") val dateEarned: String? = null,
    @SerialName("DateEarnedHardcore") val dateEarnedHardcore: String? = null,
) {
    val earned: Boolean get() = dateEarned != null || dateEarnedHardcore != null
    val badgeUrl: String get() = RaMediaUrls.badge(badgeName)
    val badgeLockedUrl: String get() = RaMediaUrls.badgeLocked(badgeName)
}

@Serializable
data class RaGameProgress(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("ImageIcon") val imageIcon: String? = null,
    @SerialName("NumAchievements") val numAchievements: Int = 0,
    @SerialName("NumAwardedToUser") val numAwardedToUser: Int = 0,
    @SerialName("NumAwardedToUserHardcore") val numAwardedToUserHardcore: Int = 0,
    @SerialName("UserCompletion") val userCompletion: String? = null,
    @SerialName("Achievements") val achievements: Map<String, RaAchievement> = emptyMap(),
) {
    val sortedAchievements: List<RaAchievement>
        get() = achievements.values.sortedBy { it.displayOrder }
}

/** Thin client for the RetroAchievements Web API (user API key auth). */
class RaClient(
    private val http: HttpClient,
) {
    /** Validates credentials by fetching the user's profile; null on failure. */
    suspend fun profile(
        username: String,
        apiKey: String,
    ): RaUserProfile? =
        try {
            val body =
                http
                    .get("$BASE/API_GetUserProfile.php") {
                        parameter("z", username)
                        parameter("y", apiKey)
                        parameter("u", username)
                    }.body<String>()
            WajihaJson.Lenient.decodeFromString<RaUserProfile>(body).takeIf { it.user.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    /** RA game id for a ROM md5, or null when unknown. */
    suspend fun gameIdForHash(
        username: String,
        apiKey: String,
        md5: String,
    ): Long? =
        try {
            val body =
                http
                    .get("$BASE/API_GetGameInfoByHash.php") {
                        parameter("z", username)
                        parameter("y", apiKey)
                        parameter("h", md5)
                    }.body<String>()
            parseRaGameIdFromHashResponse(body)
        } catch (_: Exception) {
            null
        }

    /** Full achievement list with the user's earned state. */
    suspend fun gameProgress(
        username: String,
        apiKey: String,
        raGameId: Long,
    ): RaGameProgress? =
        try {
            val body =
                http
                    .get("$BASE/API_GetGameInfoAndUserProgress.php") {
                        parameter("z", username)
                        parameter("y", apiKey)
                        parameter("g", raGameId)
                        parameter("u", username)
                    }.body<String>()
            WajihaJson.Lenient.decodeFromString<RaGameProgress>(body).takeIf { it.id > 0 }
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val BASE = "https://retroachievements.org/API"
    }
}
