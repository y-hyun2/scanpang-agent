package com.scanpang.app.data

import android.content.Context
import org.json.JSONArray

class SearchHistoryPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getRecent().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        while (current.size > MAX_ITEMS) current.removeAt(current.lastIndex)
        saveList(current)
    }

    fun remove(query: String) {
        val current = getRecent().toMutableList()
        if (current.remove(query)) saveList(current)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private fun saveList(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "scanpang_search_history"
        private const val KEY_RECENT = "recent_queries"
        private const val MAX_ITEMS = 30
    }
}
