# Lost and Found App

A comprehensive Android application for PNP Calasiao designed to help users report lost or found items within Calasiao. The system features intelligent item matching, real-time chat, and an administrative portal for claim verification.

## 📱 Get the App

You can quickly get started by downloading and installing the pre-built APK:

1. **Download APK**: [📲 Download app](https://github.com/arislenardo/Lostandfound/releases/download/Release/Balik-Calasiao.apk)
2. **Install**: Enable "Install from Unknown Sources" in your Android settings, then open the downloaded file.

---

## 🚀 Key Features

- **Item Reporting**: Easily report lost or found items with descriptions, categories, and locations.
- **Intelligent Matching**: Uses text and image similarity to automatically suggest potential matches between lost and found reports.
- **Real-time Messaging**: Built-in chat system for admins and users to communicate and coordinate item returns.
- **Admin Portal**: A dedicated web interface for administrators to manage reports, verify claims, and monitor system activity.
- **Push Notifications**: Stay updated on new matches and incoming messages.
- **Category Browsing**: Explore items by category for easier discovery.

## 🛠 Technology Stack

- **Android**: Kotlin, Jetpack Compose, Material 3
- **Backend/Database**: Firebase (Firestore, Authentication, Cloud Functions, Cloud Messaging)
- **Deployment**: Firebase Hosting (for Admin Portal)

## 🛠 Developer Installation & Setup

If you're a developer and want to build the project from scratch, follow these steps:

### Prerequisites
- Android Studio (Ladybug or newer)
- Java 17+
- A Firebase Project (configured with Google-services.json)

### Steps
1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd Lostandfound
   ```
2. **Setup Firebase**:
   - Create a new project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App to your Firebase project and download the `google-services.json`.
   - Place `google-services.json` in the `app/` directory.
   - Enable **Firestore**, **Authentication** (Email/Password & Google), and **Storage**.
3. **Configure Admin Portal**:
   - Navigate to the `functions/` directory and run `npm install`.
   - Deploy functions: `firebase deploy --only functions`.
   - Deploy hosting: `firebase deploy --only hosting`.
4. **Build and Run**:
   - Open the project in Android Studio.
   - Sync Gradle files.
   - Run the app on an emulator or physical device.

## 👥 Contributors
- DAYOnamics
