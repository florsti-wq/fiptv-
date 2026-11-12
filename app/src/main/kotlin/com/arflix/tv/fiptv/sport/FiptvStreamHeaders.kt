package com.arflix.tv.fiptv.sport

import java.util.concurrent.ConcurrentHashMap

/**
 * fIPTV: Addon-Streams aus dem Sport-Bereich brauchen oft eigene Kopfzeilen (Referer, User-Agent …).
 * Die Player-Route kennt nur die Adresse – deshalb merken wir die Kopfzeilen hier je Adresse,
 * und der Player hängt sie beim direkten Start wieder an.
 */
object FiptvStreamHeaders {
    private val map = ConcurrentHashMap<String, Map<String, String>>()
    fun put(url: String, headers: Map<String, String>) { if (headers.isNotEmpty()) map[url] = headers else map.remove(url) }
    fun get(url: String?): Map<String, String>? = url?.let { map[it] }
}
