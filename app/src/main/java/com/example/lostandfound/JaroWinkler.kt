package com.example.lostandfound

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

// Haversine Formula to calculate distance in Kilometers
fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0 // Radius of earth in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

// Updated function that USES the Jaro-Winkler Object AND Location
fun findPotentialMatches(
    query: String, 
    description: String, 
    searchLat: Double?, 
    searchLon: Double?, 
    foundItems: List<FoundItem>
): List<Pair<FoundItem, Double>> {
    if (query.isBlank()) return emptyList()

    return foundItems.map { item ->
        // 1. Calculate Score for Name
        val nameScore = JaroWinkler.similarity(query, item.name)
        
        // 2. Calculate Score for Description
        val descScore = JaroWinkler.similarity(query, item.description)
        val descriptionMatchScore = JaroWinkler.similarity(description, item.description)

        // 3. Containment Bonus
        val queryInName = if (item.name.contains(query, ignoreCase = true)) 0.9 else 0.0
        val queryInDesc = if (item.description.contains(query, ignoreCase = true)) 0.75 else 0.0

        // 4. Base Score (Textual)
        var finalScore = max(nameScore, max(descScore, max(descriptionMatchScore, max(queryInName, queryInDesc))))
        
        // 5. Location Check (If both have valid coordinates)
        if (searchLat != null && searchLon != null && item.latitude != null && item.longitude != null) {
            val distanceKm = calculateDistanceKm(searchLat, searchLon, item.latitude, item.longitude)
            
            if (distanceKm < 1.0) {
                // Very close (< 1km): Small Boost
                finalScore += 0.05
            } else if (distanceKm > 20.0) {
                // Far away (> 20km): Penalty
                finalScore -= 0.2
            } else if (distanceKm > 50.0) {
                // Very far (> 50km): Major Penalty
                finalScore -= 0.5
            }
        }

        // Clamp score between 0.0 and 1.0
        finalScore = min(1.0, max(0.0, finalScore))

        item to finalScore
    }.filter { (_, score) ->
        score > 0.70 // Threshold slightly lowered to accommodate distance logic adjustments
    }.sortedByDescending { (_, score) ->
        score
    }
}

// Matches function for checking LostItems when reporting a FoundItem
fun findLostMatches(
    query: String, 
    description: String, 
    searchLat: Double?, 
    searchLon: Double?, 
    lostItems: List<LostItem>
): List<Pair<LostItem, Double>> {
    if (query.isBlank()) return emptyList()

    return lostItems.map { item ->
        // 1. Calculate Score for Name
        val nameScore = JaroWinkler.similarity(query, item.name)
        
        // 2. Calculate Score for Description
        val descScore = JaroWinkler.similarity(query, item.description)
        val descriptionMatchScore = JaroWinkler.similarity(description, item.description)

        // 3. Containment Bonus
        val queryInName = if (item.name.contains(query, ignoreCase = true)) 0.9 else 0.0
        val queryInDesc = if (item.description.contains(query, ignoreCase = true)) 0.75 else 0.0

        // 4. Base Score
        var finalScore = max(nameScore, max(descScore, max(descriptionMatchScore, max(queryInName, queryInDesc))))

        // 5. Location Check
        if (searchLat != null && searchLon != null && item.latitude != null && item.longitude != null) {
            val distanceKm = calculateDistanceKm(searchLat, searchLon, item.latitude, item.longitude)
            
            if (distanceKm < 1.0) {
                finalScore += 0.05
            } else if (distanceKm > 20.0) {
                finalScore -= 0.2
            } else if (distanceKm > 50.0) {
                finalScore -= 0.5
            }
        }
        
        // Clamp score
        finalScore = min(1.0, max(0.0, finalScore))
        
        item to finalScore
    }.filter { (_, score) ->
        score > 0.70
    }.sortedByDescending { (_, score) ->
        score
    }
}
