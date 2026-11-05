package com.efprojects.vombatsu_client.content

private const val IMAGE_HOST = "https://img-2.vombat.su"
private const val VIDEO_HOST = "https://video-2.vombat.su"
private const val API_HOST = "https://api.vombat.su"

private fun join(host: String, vararg parts: String) =
    (listOf(host) + parts.map { it.trim('/') }).joinToString("/")

fun imageUrl(path: String, widthPx: Int): String {
    val size = when {
        widthPx <= 160 -> "xs"
        widthPx <= 480 -> "sm"
        widthPx <= 960 -> "md"
        widthPx <= 1600 -> "lg"
        else -> "xl"
    }
    return join(IMAGE_HOST, size, path.replace("/media/", "/"))
}

fun rawImageUrl(path: String) = join(IMAGE_HOST, path)

fun videoUrl(path: String) = join(VIDEO_HOST, path)

fun gifVideoUrl(path: String) = join(API_HOST, "convert-gif/mp4", path) + "?class=max"

fun gifThumbnailUrl(path: String) = join(API_HOST, "convert-gif/jpg", path) + "?class=max"
