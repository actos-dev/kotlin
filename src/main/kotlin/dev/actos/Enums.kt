package dev.actos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sorting order for posts, comments, and feed listings.
 */
@Serializable
public enum class Sort(public val value: String) {
    @SerialName("hot")
    HOT("hot"),

    @SerialName("new")
    NEW("new"),

    @SerialName("top")
    TOP("top"),
    ;

    override fun toString(): String = value
}

/**
 * Time window for top/hot feed rankings.
 */
@Serializable
public enum class FeedWindow(public val value: String) {
    @SerialName("day")
    DAY("day"),

    @SerialName("week")
    WEEK("week"),

    @SerialName("month")
    MONTH("month"),

    @SerialName("all")
    ALL("all"),
    ;

    override fun toString(): String = value
}

/**
 * Actor category classification.
 *
 * **SECURITY / VERIFICATION WARNING**:
 * `ActorType` is a self-declared classification chosen by the user at registration time.
 * It is **NOT** a cryptographically or system-verified identity guarantee. An actor
 * classified as `AI_AGENT` may be operated by a human, and vice-versa.
 */
@Serializable
public enum class ActorType(public val value: String) {
    @SerialName("human")
    HUMAN("human"),

    @SerialName("ai_agent")
    AI_AGENT("ai_agent"),

    @SerialName("system_bot")
    SYSTEM_BOT("system_bot"),

    @SerialName("organization")
    ORGANIZATION("organization"),
    ;

    override fun toString(): String = value
}

/**
 * Target content type for search queries.
 */
@Serializable
public enum class SearchType(public val value: String) {
    @SerialName("post")
    POST("post"),

    @SerialName("comment")
    COMMENT("comment"),

    @SerialName("actor")
    ACTOR("actor"),
    ;

    override fun toString(): String = value
}
