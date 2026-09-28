package com.example.data.remote

object JioSaavnImageResolver {

    fun resolve(url: String?, size: Int = 500): String {
        if (url.isNullOrBlank()) return ""
        var resolved = url.replace("\\/", "/")
        if (resolved.startsWith("http://")) {
            resolved = "https://" + resolved.substring(7)
        }

        val targetDimension = "${size}x${size}"
        resolved = resolved
            .replace("150x150", targetDimension)
            .replace("50x50", targetDimension)
            .replace("250x250", targetDimension)

        return resolved
    }

    fun unescapeHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&rdquo;", "\"")
            .replace("&ldquo;", "\"")
            .trim()
    }
}
