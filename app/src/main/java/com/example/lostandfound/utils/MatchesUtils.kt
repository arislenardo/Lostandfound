package com.example.lostandfound.utils

import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import kotlin.math.*

/**
 * Scoring weights:
 *  - Name similarity  : up to 0.55  (Jaro-Winkler)
 *  - Description sim  : up to 0.20  (Jaro-Winkler)
 *  - Exact keyword hit: 0.45 bonus  (name contains target)
 *  - Category match   : +0.25 bonus (exact same category)
 *  Threshold to surface a match: 0.65
 */

fun findPotentialMatches(
    targetName: String,
    targetDesc: String,
    targetCategory: String = "",
    itemsInDb: List<FoundItem>
): List<Pair<FoundItem, Double>> {

    if (targetName.isBlank()) return emptyList()

    return itemsInDb.map { item ->
        // Text similarity on name and description
        val nameScore = JaroWinkler.similarity(targetName, item.name) * 0.55
        val descScore = JaroWinkler.similarity(targetDesc, item.description) * 0.20
        val keywordHit = if (item.name.contains(targetName, ignoreCase = true)) 0.45 else 0.0

        // Category bonus: exact match gives a meaningful boost
        val categoryBonus = if (
            targetCategory.isNotBlank() &&
            item.category.equals(targetCategory, ignoreCase = true)
        ) 0.25 else 0.0

        val finalScore = min(1.0, max(nameScore, max(descScore, keywordHit)) + categoryBonus)
        item to finalScore
    }
        .filter { it.second > 0.65 }
        .sortedByDescending { it.second }
}

fun findLostMatches(
    targetName: String,
    targetDesc: String,
    targetCategory: String = "",
    itemsInDb: List<LostItem>
): List<Pair<LostItem, Double>> {

    if (targetName.isBlank()) return emptyList()

    return itemsInDb.map { item ->
        val nameScore = JaroWinkler.similarity(targetName, item.name) * 0.55
        val descScore = JaroWinkler.similarity(targetDesc, item.description) * 0.20
        val keywordHit = if (item.name.contains(targetName, ignoreCase = true)) 0.45 else 0.0

        val categoryBonus = if (
            targetCategory.isNotBlank() &&
            item.category.equals(targetCategory, ignoreCase = true)
        ) 0.25 else 0.0

        val finalScore = min(1.0, max(nameScore, max(descScore, keywordHit)) + categoryBonus)
        item to finalScore
    }
        .filter { it.second > 0.65 }
        .sortedByDescending { it.second }
}