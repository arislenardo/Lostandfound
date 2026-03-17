# Database Schema & Data Models

This document describes the Firestore database structure and the data models used in the Lost & Found application.

## 1. Firestore Collections

### `found_items`
Stores reports of items that have been found.
- `id` (String): Unique identifier.
- `userId` (String): ID of the user who reported the item.
- `name` (String): Title/Name of the item.
- `description` (String): Detailed description.
- `category` (String): Item category (e.g., Electronics, Keys).
- `location` (String): Descriptive location.
- `latitude` / `longitude` (Double): Geolocation coordinates.
- `status` (String): `FOUND`, `CLAIMED`, `RETURNED`.
- `imageUrl` (String): URL to the item photo in Firebase Storage.
- `createdAt` (Timestamp): Date of report.

### `lost_items`
Stores reports of items that have been lost.
- `id` (String): Unique identifier.
- `userId` (String): ID of the user who lost the item.
- `name` (String): Title/Name of the item.
- `description` (String): Detailed description.
- `category` (String): Item category.
- `location` (String): Last known location.
- `status` (String): `LOST`, `FOUND_MATCHED`, `RETURNED`.
- `createdAt` (Timestamp): Date of report.

### `users`
User profiles and roles.
- `uid` (String): Firebase Auth UID.
- `name` (String): Full name.
- `email` (String): Email address.
- `phoneNumber` (String): Contact number.
- `role` (String): `Resident` or `Admin`.

### `chats`
Conversation metadata and messages.
- `participants` (Array): List of user UIDs in the chat.
- `lastMessage` (String): Recent message snippet.
- `updatedAt` (Timestamp): Last activity time.

## 2. Relationships
- **Matches**: Logic-based matching between `lost_items` and `found_items` based on `category` and `name` similarity.
- **Claims**: An admin-verified link between a `found_item` and a `lost_item`.
- **Interactions**: Users interact via `chats` linked to specific items.
