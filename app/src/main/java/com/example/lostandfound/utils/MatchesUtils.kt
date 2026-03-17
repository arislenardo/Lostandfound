package com.example.lostandfound.utils

import com.example.lostandfound.model.FoundItem
import com.example.lostandfound.model.LostItem
import kotlin.math.*

/**
 * Matching pipeline — IMAGE-PRIMARY:
 *
 *  Image is the PRIMARY matching signal (as required). Text signals supplement it.
 *
 *  Real-world calibration:
 *    Two different photos of the SAME object (different angle/lighting) typically produce
 *    MobileNetV3 cosine similarity of 0.35–0.65. The scoring and threshold are calibrated
 *    around this range so real matches are not missed.
 *
 *  Scoring (additive, max 1.0):
 *    - Image cosine similarity  : × 0.80  → up to 0.80  [PRIMARY]
 *    - Name Text Similarity      : × 0.30  → up to 0.30  [supplement]
 *    - Keyword bonus            : +0.20 partial / +0.30 exact name match [supplement]
 *
 *  Threshold: 0.30
 *    - Good image + matching name   → ~0.70–1.00  ✅
 *    - Moderate image + similar name → ~0.45–0.65  ✅
 *    - Moderate image alone (0.50)  → 0.35  ✅ (just passes)
 *    - Weak image + unrelated name  → ~0.10–0.25  ❌
 *    - Exact same name, no image    → ~0.50  ✅ (text-only fallback)
 *
 *  Category: hard pre-filter when selected (eliminates unrelated item types immediately).
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

    // Hard category pre-filter
    val pool = if (targetCategory.isNotBlank()) {
        itemsInDb.filter { it.category.equals(targetCategory, ignoreCase = true) }
    } else {
        itemsInDb
    }

    return pool.map { item ->
        val hasVectors = targetVector.isNotEmpty() && item.imageVector.isNotEmpty()

        // PRIMARY: image cosine similarity (up to 0.80)
        val imageScore = if (hasVectors) cosineSimilarity(targetVector, item.imageVector) * 0.80 else 0.0

        // SUPPLEMENT: name-only text signals (description is extra detail, not a matching signal)
        val nameScore    = TextSimilarity.similarity(targetName, item.name) * 0.30
        val keywordBonus = when {
            item.name.equals(targetName, ignoreCase = true)         -> 0.30  // exact
            item.name.contains(targetName, ignoreCase = true) ||
            targetName.contains(item.name, ignoreCase = true)       -> 0.20  // partial
            else                                                     -> 0.0
        }

        val finalScore = min(1.0, imageScore + nameScore + keywordBonus)
        item to finalScore
    }
        .filter { it.second > 0.30 }
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

    // Hard category pre-filter
    val pool = if (targetCategory.isNotBlank()) {
        itemsInDb.filter { it.category.equals(targetCategory, ignoreCase = true) }
    } else {
        itemsInDb
    }

    return pool.map { item ->
        val hasVectors = targetVector.isNotEmpty() && item.imageVector.isNotEmpty()

        // PRIMARY: image cosine similarity (up to 0.80)
        val imageScore = if (hasVectors) cosineSimilarity(targetVector, item.imageVector) * 0.80 else 0.0

        // SUPPLEMENT: name-only text signals (description is extra detail, not a matching signal)
        val nameScore    = TextSimilarity.similarity(targetName, item.name) * 0.30
        val keywordBonus = when {
            item.name.equals(targetName, ignoreCase = true)         -> 0.30  // exact
            item.name.contains(targetName, ignoreCase = true) ||
            targetName.contains(item.name, ignoreCase = true)       -> 0.20  // partial
            else                                                     -> 0.0
        }

        val finalScore = min(1.0, imageScore + nameScore + keywordBonus)
        item to finalScore
    }
        .filter { it.second > 0.30 }
        .sortedByDescending { it.second }
}