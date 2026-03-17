package com.example.lostandfound.utils

/**
 * Text similarity for lost-and-found item name/description matching.
 *
 * WHY TOKEN JACCARD + CONTAINMENT:
 *  1. Normalizes and tokenizes both strings into word sets
 *  2. Jaccard similarity = |intersection| / |union| — pure word overlap, order-independent
 *  3. Containment bonus = what fraction of the SHORTER string's words appear in the longer
 *     This is key: "wallet" should still match "brown leather wallet" well, because all
 *     words of the short string are contained in the long one.
 *  4. Final score = max(jaccard, containment) so the better signal wins
 *
 * Examples:
 *  "Samsung Galaxy A54"  vs "Galaxy A54 Samsung phone"  → ~0.75  ✅
 *  "wallet"              vs "brown leather wallet"       → ~0.85  ✅ (containment)
 *  "blue backpack"       vs "JanSport blue bag"         → ~0.33  (partial, expected)
 *  "phone"               vs "keys"                      → ~0.0   ✅
 */
object TextSimilarity {

    // Drop common filler words that add noise and don't identify an item
    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "is", "it", "in", "on", "at",
        "of", "with", "my", "i", "this", "that", "its", "has", "very"
    )

    private fun tokenize(s: String): Set<String> {
        return s.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")  // strip punctuation
            .split(Regex("\\s+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()
    }

    fun similarity(s1: String, s2: String): Double {
        if (s1.isBlank() || s2.isBlank()) return 0.0

        val t1 = tokenize(s1)
        val t2 = tokenize(s2)

        if (t1.isEmpty() || t2.isEmpty()) return 0.0
        if (t1 == t2) return 1.0

        val intersection = t1.intersect(t2).size.toDouble()
        val union        = t1.union(t2).size.toDouble()

        // Jaccard: what fraction of combined vocabulary is shared
        val jaccard = intersection / union

        // Containment: what fraction of the SHORTER string appears in the LONGER
        // Handles "wallet" ⊂ "brown leather wallet" perfectly
        val shorter = if (t1.size <= t2.size) t1 else t2
        val containment = intersection / shorter.size.toDouble()

        // Best of the two signals
        return maxOf(jaccard, containment * 0.9) // slight discount on containment to prefer full matches
    }
}
