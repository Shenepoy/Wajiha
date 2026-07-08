package com.wajiha.data.ra

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RaUserProfile(
    @SerialName("User") val user: String = "",
    @SerialName("TotalPoints") val totalPoints: Int = 0,
    @SerialName("TotalTruePoints") val totalTruePoints: Int = 0,
    @SerialName("UserPic") val userPic: String? = null
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
    @SerialName("DateEarnedHardcore") val dateEarnedHardcore: String? = null
) {
    val earned: Boolean get() = dateEarned != null || dateEarnedHardcore != null
    val badgeUrl: String get() = "https://media.retroachievements.org/Badge/$badgeName.png"
    val badgeLockedUrl: String get() = "https://media.retroachievements.org/Badge/${badgeName}_lock.png"
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
    @SerialName("Achievements") val achievements: Map<String, RaAchievement> = emptyMap()
) {
    val sortedAchievements: List<RaAchievement>
        get() = achievements.values.sortedBy { it.displayOrder }
}

/** Thin client for the RetroAchievements Web API (user API key auth). */
class RaClient(private val http: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Validates credentials by fetching the user's profile; null on failure. */
    suspend fun profile(username: String, apiKey: String): RaUserProfile? = try {
        val body = http.get("$BASE/API_GetUserProfile.php") {
            parameter("z", username)
            parameter("y", apiKey)
            parameter("u", username)
        }.body<String>()
        json.decodeFromString<RaUserProfile>(body).takeIf { it.user.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** RA game id for a ROM md5, or null when unknown. */
    suspend fun gameIdForHash(username: String, apiKey: String, md5: String): Long? = try {
        val body = http.get("$BASE/API_GetGameInfoByHash.php") {
            parameter("z", username)
            parameter("y", apiKey)
            parameter("h", md5)
        }.body<String>()
        json.decodeFromString<HashEnvelope>(body).id?.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }

    /** Full achievement list with the user's earned state. */
    suspend fun gameProgress(username: String, apiKey: String, raGameId: Long): RaGameProgress? = try {
        val body = http.get("$BASE/API_GetGameInfoAndUserProgress.php") {
            parameter("z", username)
            parameter("y", apiKey)
            parameter("g", raGameId)
            parameter("u", username)
        }.body<String>()
        json.decodeFromString<RaGameProgress>(body).takeIf { it.id > 0 }
    } catch (_: Exception) {
        null
    }

    @Serializable
    private data class HashEnvelope(@SerialName("ID") val id: Long? = null)

    private companion object {
        const val BASE = "https://retroachievements.org/API"
    }
}
