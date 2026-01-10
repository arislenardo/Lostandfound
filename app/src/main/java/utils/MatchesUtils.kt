package com.example.lostandfound.utils

import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import com.example.lostandfound.utils.JaroWinkler
import com.example.lostandfound.utils.calculateDistanceKm
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
        if (targetLat != null && targetLon != null && item.latitude != null && item.longitude != null) {
            val distanceKm = calculateDistanceKm(targetLat, targetLon, item.latitude, item.longitude)
            if (distanceKm < 2.0) finalScore += 0.1  // Bonus for being very close
            else if (distanceKm > 20.0) finalScore -= 0.2 // Penalty for being far
        }

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

        if (targetLat != null && targetLon != null && item.latitude != null && item.longitude != null) {
            val distanceKm = calculateDistanceKm(targetLat, targetLon, item.latitude, item.longitude)
            if (distanceKm < 2.0) finalScore += 0.1
        }

        item to min(1.0, max(0.0, finalScore))
    }
        .filter { it.second > 0.65 }
        .sortedByDescending { it.second }
}