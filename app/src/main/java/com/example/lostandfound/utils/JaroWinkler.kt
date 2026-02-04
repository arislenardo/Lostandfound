package com.example.lostandfound.utils

import kotlin.math.*

// --- ALGORITHM UTILS (JARO-WINKLER IMPLEMENTATION) ---

object JaroWinkler {
    fun similarity(s1: String, s2: String): Double {
        val normalized1 = s1.lowercase()
        val normalized2 = s2.lowercase()

        if (normalized1 == normalized2) return 1.0

        val matchDistance = (max(normalized1.length, normalized2.length) / 2) - 1
        val s1Matches = BooleanArray(normalized1.length)
        val s2Matches = BooleanArray(normalized2.length)
        var matches = 0.0
        var transpositions = 0.0

        for (i in normalized1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, normalized2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (normalized1[i] != normalized2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        if (matches == 0.0) return 0.0

        var k = 0
        for (i in normalized1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (normalized1[i] != normalized2[k]) transpositions++
            k++
        }

        val jaro = (matches / normalized1.length + matches / normalized2.length + (matches - transpositions / 2) / matches) / 3.0

        // Winkler Prefix Bonus (Standard 0.1 scaling)
        var prefix = 0
        for (i in 0 until min(normalized1.length, min(normalized2.length, 4))) {
            if (normalized1[i] == normalized2[i]) prefix++ else break
        }

        return jaro + 0.1 * prefix * (1.0 - jaro)
    }
}


