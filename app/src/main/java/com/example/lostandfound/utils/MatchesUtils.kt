package com.example.lostandfound.utils

import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import kotlin.math.*

fun findPotentialMatches(
    targetName: String,
    targetDesc: String,
    targetLat: Double?,
    targetLon: Double?,
    itemsInDb: List<FoundItem>
): List<Pair<FoundItem, Double>> {

    if (targetName.isBlank()) return emptyList()

    return itemsInDb.map { item ->
        // 1. Use your JaroWinkler math for the name
        val nameScore = JaroWinkler.similarity(targetName, item.name)

        // 2. Use JaroWinkler for description
        val descScore = JaroWinkler.similarity(targetDesc, item.description)

        // 3. Simple keyword check
        val queryInName = if (item.name.contains(targetName, ignoreCase = true)) 0.9 else 0.0

        // Take the best text score
        var finalScore = max(nameScore, max(descScore, queryInName))

        // 4. Geographic Weighting
        // 4. Geographic Weighting removed as requested

        finalScore = min(1.0, max(0.0, finalScore))
        item to finalScore
    }
        .filter { it.second > 0.65 } // Threshold
        .sortedByDescending { it.second }
}

fun findLostMatches(
    targetName: String,
    targetDesc: String,
    targetLat: Double?,
    targetLon: Double?,
    itemsInDb: List<LostItem> // This version takes LostItems
): List<Pair<LostItem, Double>> {
    if (targetName.isBlank()) return emptyList()

    return itemsInDb.map { item ->
        val nameScore = JaroWinkler.similarity(targetName, item.name)
        val descScore = JaroWinkler.similarity(targetDesc, item.description)

        var finalScore = max(nameScore, descScore)

        // 4. Geographic Weighting removed as requested

        item to min(1.0, max(0.0, finalScore))

        item to min(1.0, max(0.0, finalScore))
    }
        .filter { it.second > 0.65 }
        .sortedByDescending { it.second }
}