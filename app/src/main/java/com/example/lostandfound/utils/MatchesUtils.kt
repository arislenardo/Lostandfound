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

/**
 * Calculates the cosine similarity between two feature vectors.
 * Returns a value between -1.0 and 1.0, where 1.0 indicates identical vectors.
 */
fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
    if (a.size != b.size || a.isEmpty()) return 0.0
    val dot   = a.zip(b).sumOf { (x, y) -> x * y }
    val normA = sqrt(a.sumOf { it * it })
    val normB = sqrt(b.sumOf { it * it })
    return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
}

/**
 * Calculates an enhanced image similarity score by combining the cosine similarity of the 
 * primary semantic embedding (MobileNetV3) and a secondary color histogram intersection.
 * This ensures matches share both semantic meaning and visual color traits.
 */
fun calculateImageSimilarity(a: List<Double>, b: List<Double>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0

    val mnSize = 1280 // MobileNet vector size
    val mnA = a.take(mnSize)
    val mnB = b.take(mnSize)
    val simMN = max(0.0, cosineSimilarity(mnA, mnB))
    
    // If color histograms are appended (backwards compatible)
    if (a.size > mnSize && b.size > mnSize) {
        val histA = a.drop(mnSize)
        val histB = b.drop(mnSize)
        val hSize = min(histA.size, histB.size)
        
        var histIntersection = 0.0
        for (i in 0 until hSize) {
            histIntersection += min(histA[i], histB[i])
        }
        
        // Softened color scoring:
        // We still use color to differentiate, but we don't "kill" the match 
        // as aggressively if the color intersection is low (common with bottles).
        val weightMN = 0.7
        val weightHist = 0.3
        
        return simMN * weightMN + histIntersection * weightHist
    }
    
    return simMN
}

/**
 * Compares a lost item's data against a database of found items to find potential matches.
 * Uses a combination of image semantic/color similarity and textual similarity.
 * Returns a list of matches paired with their similarity score (0.0 to 1.0), sorted descending.
 */
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

        // PRIMARY: image cosine similarity + color
        val imageScore = if (hasVectors) calculateImageSimilarity(targetVector, item.imageVector) * 0.80 else 0.0

        // SUPPLEMENT: name-only text signals (description is extra detail, not a matching signal)
        val nameScore    = TextSimilarity.similarity(targetName, item.name) * 0.30
        
        val exactName = item.name.equals(targetName, ignoreCase = true)
        val partialName = (item.name.contains(targetName, ignoreCase = true) && targetName.length > 3) ||
                          (targetName.contains(item.name, ignoreCase = true) && item.name.length > 3)

        val keywordBonus = when {
            exactName -> 0.30  // exact
            partialName -> 0.20  // partial
            else -> 0.0
        }

        val finalScore = min(1.0, imageScore + nameScore + keywordBonus)
        item to finalScore
    }
        .filter { it.second > 0.32 } // Lowered threshold to allow more "potential" matches for admin review
        .sortedByDescending { it.second }
}

/**
 * Compares a found item's data against a database of lost items to find potential matches.
 * Uses the same additive scoring algorithm (image similarity + text similarity + keyword bonus)
 * as `findPotentialMatches`. Returns matches scoring above a predefined threshold.
 */
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

        // PRIMARY: image cosine similarity + color
        val imageScore = if (hasVectors) calculateImageSimilarity(targetVector, item.imageVector) * 0.80 else 0.0

        // SUPPLEMENT: name-only text signals (description is extra detail, not a matching signal)
        val nameScore    = TextSimilarity.similarity(targetName, item.name) * 0.30
        
        val exactName = item.name.equals(targetName, ignoreCase = true)
        val partialName = (item.name.contains(targetName, ignoreCase = true) && targetName.length > 3) ||
                          (targetName.contains(item.name, ignoreCase = true) && item.name.length > 3)

        val keywordBonus = when {
            exactName -> 0.30  // exact
            partialName -> 0.20  // partial
            else -> 0.0
        }

        val finalScore = min(1.0, imageScore + nameScore + keywordBonus)
        item to finalScore
    }
        .filter { it.second > 0.32 } // Lowered threshold to allow more "potential" matches for admin review
        .sortedByDescending { it.second }
}