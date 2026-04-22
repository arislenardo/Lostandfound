package com.example.lostandfound.utils

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Utility function to perform reverse geocoding.
 * Converts latitude and longitude coordinates into a human-readable address string
 * (e.g., "Street Name, District, City").
 */
suspend fun getReadableAddress(context: Context, latitude: Double, longitude: Double): String {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                
                // Construct a readable address (Feature/Street, Area, City)
                val parts = mutableListOf<String>()
                
                // 1. Street or Building Name
                val feature = address.featureName
                val thoroughfare = address.thoroughfare
                if (!feature.isNullOrBlank()) parts.add(feature)
                else if (!thoroughfare.isNullOrBlank()) parts.add(thoroughfare)
                
                // 2. District/Barangay
                val subLocality = address.subLocality
                if (!subLocality.isNullOrBlank() && subLocality != feature) parts.add(subLocality)
                
                // 3. City
                val locality = address.locality
                if (!locality.isNullOrBlank() && locality != subLocality) parts.add(locality)

                if (parts.isNotEmpty()) {
                    parts.joinToString(", ")
                } else {
                    address.getAddressLine(0) ?: String.format("%.4f, %.4f", latitude, longitude)
                }
            } else {
                String.format("%.4f, %.4f", latitude, longitude)
            }
        } catch (e: Exception) {
            String.format("%.4f, %.4f", latitude, longitude)
        }
    }
}
