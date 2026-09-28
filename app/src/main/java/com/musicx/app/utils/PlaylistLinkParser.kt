package com.musicx.app.utils

object PlaylistLinkParser {
    /**
     * Extracts token from a JioSaavn playlist URL or returns the token if input is a direct token string.
     * Examples:
     * - https://www.jiosaavn.com/featured/romantic-hits/c9_7sW8q_
     * - https://www.jiosaavn.com/s/playlist/user/my-favs/v1h3k5L
     * - https://jiosaavn.com/featured/.../xyz123?autoplay=true
     * - c9_7sW8q_ (direct token)
     */
    fun extractToken(url: String): String? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null
        val regex = Regex("""jiosaavn\.com/(?:featured|s/playlist)/[^/]+/([^/?&#]+)""")
        val match = regex.find(cleanUrl)
        val extracted = match?.groupValues?.get(1) ?: cleanUrl.substringAfterLast("/").substringBefore("?")
        return extracted.trim().takeIf { it.isNotBlank() }
    }
}
