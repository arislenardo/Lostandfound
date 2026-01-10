package com.example.lostandfound

import com.google.firebase.firestore.FirebaseFirestore

// Utility function to clear current data and seed random items
fun seedDatabase(onResult: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    
    // 1. Fetch all found items to delete
    db.collection("found_items").get().addOnSuccessListener { foundSnapshot ->
        val batch = db.batch()
        for (doc in foundSnapshot.documents) {
            batch.delete(doc.reference)
        }
        
        // 2. Fetch all lost items to delete
        db.collection("lost_items").get().addOnSuccessListener { lostSnapshot ->
            for (doc in lostSnapshot.documents) {
                batch.delete(doc.reference)
            }
            
            // 3. Commit deletions
            batch.commit().addOnSuccessListener {
                // 4. Add Mock Data
                addMockData(db, onResult)
            }.addOnFailureListener { e ->
                onResult("Error clearing data: ${e.localizedMessage}")
            }
        }
    }.addOnFailureListener { e ->
        onResult("Error fetching data: ${e.localizedMessage}")
    }
}

private fun addMockData(db: FirebaseFirestore, onResult: (String) -> Unit) {
    // Coordinates around a central point (e.g., a city center)
    // Lat: 16.0191, Lon: 120.3636 (Calasiao, Pangasinan roughly)
    
    val foundItems = listOf(
        FoundItem(name = "Blue Umbrella", description = "Small travel umbrella", location = "Central Park", latitude = 16.0195, longitude = 120.3630, category = "Accessories", dateFoundText = "2023-11-01", email = "finder@test.com"),
        FoundItem(name = "Car Keys", description = "Toyota keys with a red keychain", location = "Main St Parking", latitude = 16.0200, longitude = 120.3640, category = "Keys", dateFoundText = "2023-11-02", email = "finder2@test.com"),
        FoundItem(name = "iPhone 12", description = "Black case, cracked screen", location = "Library", latitude = 16.0180, longitude = 120.3620, category = "Electronics", dateFoundText = "2023-11-03", email = "finder@test.com"),
        FoundItem(name = "Brown Wallet", description = "Leather wallet, no ID", location = "Bus Station", latitude = 16.0210, longitude = 120.3650, category = "Accessories", dateFoundText = "2023-11-04", email = ""), // No email case
        FoundItem(name = "Aquaflask", description = "Blue 32oz water bottle", location = "Gym", latitude = 16.0190, longitude = 120.3635, category = "Accessories", dateFoundText = "2023-11-05", email = "gym_staff@test.com")
    )

    val lostItems = listOf(
        LostItem(name = "Gold Ring", description = "Wedding band with inscription", location = "Gym", latitude = 16.0190, longitude = 120.3635, category = "Accessories", dateLost = "2023-10-30", email = "owner@test.com"),
        LostItem(name = "Textbook", description = "Calculus Vol 2", location = "Library", latitude = 16.0180, longitude = 120.3620, category = "Documents", dateLost = "2023-11-01", email = "student@test.com"),
        LostItem(name = "Tumbler", description = "Blue water bottle", location = "Gym", latitude = 16.0192, longitude = 120.3632, category = "Accessories", dateLost = "2023-11-05", email = "owner2@test.com")
    )

    var opsCount = 0
    val totalOps = foundItems.size + lostItems.size
    
    // Helper to track completion
    fun checkComplete() {
        opsCount++
        if (opsCount == totalOps) {
            onResult("Database Reset & Seeded Successfully!")
        }
    }

    foundItems.forEach { 
        db.collection("found_items").add(it).addOnCompleteListener { checkComplete() } 
    }
    lostItems.forEach { 
        db.collection("lost_items").add(it).addOnCompleteListener { checkComplete() } 
    }
}
