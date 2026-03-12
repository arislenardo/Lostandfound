package com.example.lostandfound.utils

import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import kotlin.math.*

/**
 * Matching pipeline:
 *  1. Hard pre-filter by category (when a category is selected) — only compare within the same category.
 *  2. Within the filtered set, score using:
 *      - Image cosine similarity : up to 0.70  (when both items have vectors)
 *      - Name similarity          : up to 0.35  (Jaro-Winkler, reduced when image is present)
 *      - Description similarity   : up to 0.20  (Jaro-Winkler)
 *      - Exact keyword hit        : 0.45 bonus
 *  3. Threshold to surface a match: 0.55
 *
 *  When no category is selected (empty string), the filter is skipped and all items are scored.
 *  When no image is present, falls back to text-only Jaro-Winkler scoring.
 */

// --- Cosine Similarity ---
fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0
    val dot   = a.zip(b).sumOf { (x, y) -> x * y }
    val normA = sqrt(a.sumOf { it * it })
    val normB = sqrt(b.sumOf { it * it })
    return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
}

// --- Match found items against a lost item report ---
fun findPotentialMatches(
    targetName: String,
    targetDesc: String,
    targetCategory: String = "",
    targetVector: List<Double> = emptyList(),
    itemsInDb: List<FoundItem>
): List<Pair<FoundItem, Double>> {

    if (targetName.isBlank() && targetVector.isEmpty()) return emptyList()

    // Hard category pre-filter: only match within the same category when one is selected
    val pool = if (targetCategory.isNotBlank()) {
        itemsInDb.filter { it.category.equals(targetCategory, ignoreCase = true) }
    } else {
        itemsInDb
    }

    return pool.map { item ->
        val hasVectors = targetVector.isNotEmpty() && item.imageVector.isNotEmpty()

        val imageScore  = if (hasVectors) cosineSimilarity(targetVector, item.imageVector) * 0.70 else 0.0
        val nameWeight  = if (hasVectors) 0.35 else 0.55
        val nameScore   = JaroWinkler.similarity(targetName, item.name) * nameWeight
        val descScore   = JaroWinkler.similarity(targetDesc, item.description) * 0.20
        val keywordHit  = if (item.name.contains(targetName, ignoreCase = true)) 0.45 else 0.0

        val textScore  = max(nameScore, max(descScore, keywordHit))
        val finalScore = min(1.0, if (hasVectors) imageScore + textScore * 0.40 else textScore)
        item to finalScore
    }
        .filter { it.second > 0.55 }
        .sortedByDescending { it.second }
}

// --- Match lost items against a found item report ---
fun findLostMatches(
    targetName: String,
    targetDesc: String,
    targetCategory: String = "",
    targetVector: List<Double> = emptyList(),
    itemsInDb: List<LostItem>
): List<Pair<LostItem, Double>> {

    if (targetName.isBlank() && targetVector.isEmpty()) return emptyList()

    // Hard category pre-filter: only match within the same category when one is selected
    val pool = if (targetCategory.isNotBlank()) {
        itemsInDb.filter { it.category.equals(targetCategory, ignoreCase = true) }
    } else {
        itemsInDb
    }

    return pool.map { item ->
        val hasVectors = targetVector.isNotEmpty() && item.imageVector.isNotEmpty()

        val imageScore  = if (hasVectors) cosineSimilarity(targetVector, item.imageVector) * 0.70 else 0.0
        val nameWeight  = if (hasVectors) 0.35 else 0.55
        val nameScore   = JaroWinkler.similarity(targetName, item.name) * nameWeight
        val descScore   = JaroWinkler.similarity(targetDesc, item.description) * 0.20
        val keywordHit  = if (item.name.contains(targetName, ignoreCase = true)) 0.45 else 0.0

        val textScore  = max(nameScore, max(descScore, keywordHit))
        val finalScore = min(1.0, if (hasVectors) imageScore + textScore * 0.40 else textScore)
        item to finalScore
    }
        .filter { it.second > 0.55 }
        .sortedByDescending { it.second }
}