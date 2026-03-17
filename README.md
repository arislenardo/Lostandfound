# Lost and Found App

A comprehensive Android application designed to help users report and find lost or found items within a community (e.g., a residential area, campus, or workplace). The system features intelligent item matching, real-time chat, and an administrative portal for claim verification.

## 🚀 Key Features

- **Item Reporting**: Easily report lost or found items with descriptions, categories, and locations.
- **Intelligent Matching**: Uses text similarity and geolocation to automatically suggest potential matches between lost and found reports.
- **Real-time Messaging**: Built-in chat system for users to communicate and coordinate item returns.
- **Admin Portal**: A dedicated web interface for administrators to manage reports, verify claims, and monitor system activity.
- **Push Notifications**: Stay updated on new matches and incoming messages.
- **Category Browsing**: Explore items by category for easier discovery.

## 🛠 Technology Stack

- **Android**: Kotlin, Jetpack Compose, Material 3
- **Backend/Database**: Firebase (Firestore, Authentication, Cloud Functions, Cloud Messaging)
- **Deployment**: Firebase Hosting (for Admin Portal)

## 📦 Project Structure

```text
Lostandfound/
├── app/                # Android Mobile App (Kotlin/Compose)
│   ├── src/main/java/  # Source code
│   └── ...
├── functions/          # Firebase Cloud Functions (Node.js)
├── public/             # Admin Portal Web Assets
├── documentation/      # Project Documentation & Proposal
└── ...
```

## 🛠 Installation & Setup

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
- [Your Name/Team Name]

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
