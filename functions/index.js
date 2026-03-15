const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();

// 1. Notify user on new Message
exports.onMessageSent = functions.firestore
    .document("messages/{messageId}")
    .onCreate(async (snap, context) => {
      const message = snap.data();

      try {
        const userDoc = await admin.firestore().collection("users").doc(message.receiverId).get();
        if (!userDoc.exists) return null;

        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) return null;

        const payload = {
          notification: {
            title: `New Message from ${message.senderName}`,
            body: message.text,
          },
          data: {
            type: "message",
            senderId: message.senderId,
            messageId: snap.id,
          },
        };

        await admin.messaging().send({
          token: fcmToken,
          notification: payload.notification,
          data: payload.data,
          android: {
            priority: "high",
            notification: {
              channelId: "lost_and_found_default_channel",
              sound: "default",
            },
          },
        });
      } catch (error) {
        console.error("Error sending message notification:", error);
      }
      return null;
    });

// 2. Notify on Claim Status Update (Approved/Denied)
exports.onClaimStatusUpdated = functions.firestore
    .document("claims/{claimId}")
    .onUpdate(async (change, context) => {
      const newValue = change.after.data();
      const previousValue = change.before.data();

      if (newValue.status === previousValue.status) return null;
      if (newValue.status !== "APPROVED" && newValue.status !== "REJECTED" && newValue.status !== "DISPUTED") return null;

      try {
        // If disputed, notify the admin who reviewed it. Else, notify the user.
        const targetUserId = newValue.status === "DISPUTED" ? newValue.reviewedBy : newValue.userId;
        if (!targetUserId) return null;

        const userDoc = await admin.firestore().collection("users").doc(targetUserId).get();
        if (!userDoc.exists) return null;

        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) return null;

        let title = "";
        let body = "";

        if (newValue.status === "DISPUTED") {
          title = "Claim Disputed";
          body = `A user has disputed your rejection for "${newValue.itemName}".`;
        } else {
          const statusText = newValue.status === "APPROVED" ? "Approved" : "Rejected";
          title = `Claim Update: ${statusText}`;
          body = `Your claim for "${newValue.itemName}" has been ${statusText.toLowerCase()}`;
        }

        const payload = {
          notification: {
            title: title,
            body: body,
          },
          data: {
            type: "claim_update",
            claimId: change.after.id,
            foundItemId: newValue.itemId,
          },
        };

        await admin.messaging().send({
          token: fcmToken,
          notification: payload.notification,
          data: payload.data,
          android: {
            priority: "high",
            notification: {
              channelId: "lost_and_found_default_channel",
              sound: "default",
            },
          },
        });
      } catch (error) {
        console.error("Error sending claim notification:", error);
      }
      return null;
    });

// 3. Notify on Potential Match
exports.onMatchFound = functions.firestore
    .document("match_notifications/{notificationId}")
    .onCreate(async (snap, context) => {
      const matchData = snap.data();

      try {
        const userDoc = await admin.firestore().collection("users").doc(matchData.lostItemOwnerId).get();
        if (!userDoc.exists) return null;

        const fcmToken = userDoc.data().fcmToken;
        if (!fcmToken) return null;

        const payload = {
          notification: {
            title: "Potential Match Found!",
            body: `We found a potential match for your lost item: "${matchData.lostItemName}"`,
          },
          data: {
            type: "match_notification",
            matchId: snap.id,
            foundItemId: matchData.foundItemId,
            lostItemId: matchData.lostItemId,
          },
        };

        await admin.messaging().send({
          token: fcmToken,
          notification: payload.notification,
          data: payload.data,
          android: {
            priority: "high",
            notification: {
              channelId: "lost_and_found_default_channel",
              sound: "default",
            },
          },
        });
      } catch (error) {
        console.error("Error sending match notification:", error);
      }
      return null;
    });
