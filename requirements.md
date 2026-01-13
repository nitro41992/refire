## **1. Product Overview**

Re-Fire is a "Universal Focus Utility" that bridges the gap between active link sharing and passive notification management. It allows users to snooze specific content or entire conversations with bespoke precision, using on-device AI to summarize missed context upon return.

---

## **2. Feature List**

### **A. Active Capture (The "Share Sheet")**

* **Share-to-Snooze:** Integrate into the system Share Sheet to capture URLs/Text.
* **Rich Scraper:** Automatically fetch Open Graph metadata (titles/images) from shared links.
* **Zero-Friction UI:** A **Modal Bottom Sheet** (TickTick-style) for rapid time-selection without leaving the host app.

### **B. Reactive Capture (The "Snooze Tray")**

* **Notification Listener:** Passively monitor the status bar for incoming alerts.
* **Dismissal Queue:** A "Recents" buffer that logs the last 10 dismissed notifications.
* **Quick Settings Tile:** A system pull-down shortcut to snooze the last thing the user swiped away.

### **C. Focus Engine (The "Mute")**

* **Conversation Suppression:** Identify unique thread IDs (`ShortcutId`) to mute specific people/groups without silencing the entire app.
* **Silent Watchdog:** Instantly "kill" (cancel) incoming pings from snoozed threads to prevent vibrations/sounds.
* **Subsequent Log:** Silently cache the text of every suppressed message for later review.

### **D. The Return (The "Re-Fire")**

* **AI Summarization:** Utilize **Gemini Nano (AICore)** to generate a bulleted summary of all messages missed during the snooze period.
* **High-Fidelity Re-Fire:** Trigger a new notification that looks like the original source app.
* **Jump-Back Launch:** A single tap on the notification opens the original app (e.g., WhatsApp or Reddit) using its package name.

---

## **3. User Stories**

* **Selective Focus:** *"As a user in a meeting, I want to snooze my family group chat for 60 minutes while keeping my boss's notifications active, so I'm not distracted by non-urgent pings."*
* **The Link Buffer:** *"As a reader, I want to share a long-form article to Re-Fire and pick 'Tonight at 9 PM' so it appears as a rich image-reminder when I am actually on my couch."*
* **Accidental Swipe Recovery:** *"As a user who accidentally swiped away a message while walking, I want to tap a Quick Settings tile to 'Snooze Last Dismissed' so I don't forget to reply later."*
* **The Message Catch-up:** *"As a focused worker, when my 2-hour snooze ends, I want to see a single AI-generated summary of the 20 messages Valerie sent, so I can grasp the context in 5 seconds."*

---

## **4. Priority List**

| Priority | Feature | Reason |
| --- | --- | --- |
| **P0 (Critical)** | **Notification Listener & Kill Command** | The core technical hurdle; if you can't "kill" pings, the app fails. |
| **P0 (Critical)** | **Share Sheet Intake & Bottom Sheet** | The primary user entry point for links. |
| **P1 (High)** | **Conversation Thread ID Matching** | Necessary for the "selective mute" value proposition. |
| **P1 (High)** | **Jump-Back Launching** | Completes the loop of returning to the original context. |
| **P2 (Medium)** | **Quick Settings Tile** | Major UX convenience but not strictly required for the MVP. |
| **P2 (Medium)** | **Gemini Nano Summaries** | The "magic" layer; can fallback to a simple list view initially. |

---

## **5. Implementation Plan**

### **Phase 1: Foundations (Week 1–2)**

* **Setup:** Initialize a Native Kotlin / Jetpack Compose project.
* **Listener Service:** Build the `NotificationListenerService` and verify it can intercept and `cancelNotification` based on package name.
* **Intake:** Implement the `ACTION_SEND` intent filter and the basic Modal Bottom Sheet.

### **Phase 2: Conversation Logic (Week 3–4)**

* **Thread Identification:** Extract `ShortcutId` and `GroupKey` from notifications to differentiate between chats.
* **Database:** Setup Room DB to store the "Snooze List" (Thread ID, End Time, Content).
* **Jump-Back:** Implement the `<queries>` manifest entry and the "Launch App" logic.

### **Phase 3: Intelligence & UI Polish (Week 5–6)**

* **The Log:** Create the background scraper that saves text from "killed" notifications.
* **AI Integration:** Connect to **AICore** to pass cached text into the **GenAI Summarization API** (Gemini Nano).
* **Refinement:** Polish the Bottom Sheet with custom time-pickers and smart presets.

### **Phase 4: Optimization (Week 7–8)**

* **Battery:** Implement "Unrestricted" battery checks and the `AlarmManager` for precise firing.
* **Permissions:** Build a robust onboarding flow to guide users through the "Notification Access" permission.

---

## **6. Architectural Decisions**

### **Thread Identification & Fallback**
* **Primary Strategy:** Extract `ShortcutId` and `GroupKey` from `StatusBarNotification` for conversation-level targeting.
* **Fallback for Missing Thread IDs:** If an app doesn't expose thread identifiers, fall back to **package-level muting** (suppress all notifications from that app). Still provides value, just less granular.

### **AI Summarization & Fallback**
* **Primary:** Use **Gemini Nano (AICore)** to generate bulleted summary of missed messages.
* **Fallback for Unavailable Gemini:** Show **simple count notification** (e.g., "You missed 20 messages from Valerie") with jump-back button. No AI required.

### **Data Persistence Strategy**
* **Snoozes:** Persist in **Room DB** and survive device reboots/force-stops via `AlarmManager` + `BOOT_COMPLETED` receiver. Critical for reliability.
* **Message Text:** Store suppressed notification text in Room DB, then **delete immediately after re-fire** (privacy-first approach).
* **Recents Buffer:** Ephemeral **in-memory only** (cleared on app restart). No persistence needed for short-term dismissal tracking.

### **Duplicate Snooze Handling**
* **Behavior:** If the user snoozes the same conversation/URL multiple times with different end times, **replace with the latest snooze** (simple override). Example: "I changed my mind, snooze until 5pm instead of 3pm."

### **Re-Fire Notification Format**
* **Content:** Show count/summary text (e.g., "20 messages from Valerie: Meeting moved, dinner at 7pm...") + **"View in App" button** for jump-back action.
* **Uninstalled Apps:** If the source app was uninstalled during snooze period, show summary text without the jump-back button.

### **Share Sheet URL Scraping**
* **Timing:** Fetch Open Graph metadata **immediately** when user shares a URL.
* **UX:** Display **loading indicator** in the Modal Bottom Sheet while scraping. User waits ~1-2 seconds but sees rich preview (title/image) before confirming snooze time.

### **Quick Settings Tile - Empty State**
* **Behavior:** If the "Recents" buffer is empty (no recently dismissed notifications), show a **toast message: "No recent dismissals"**. Do not open the app or show error UI.

### **Snooze Auto-Cancellation**
* **Manual App Opening:** If a user manually opens the snoozed app/conversation **during** the snooze period, **keep the snooze active** (do not auto-cancel).
* **Rationale:** User may just be checking something else in the app. Still re-fire later with summary of missed messages.

### **Security & Privacy**
* **Sensitive Apps:** No special handling for banking, 2FA, or password manager apps. **User controls what they snooze.**
* **Message Storage:** Suppressed message text stored temporarily in Room DB (encrypted by Android OS). Deleted post-summarization.

---

## **7. Implementation Status (Phase 2.5 Complete)**

### **What's Working**
* ✅ **Core Snooze Functionality:** Full end-to-end snooze flow from notification → dismiss → schedule → re-fire
* ✅ **Smart Filtering:** System/OEM/ongoing notifications filtered out automatically
* ✅ **Dual Permission Flow:** Notification Access + POST_NOTIFICATIONS (Android 13+) with clear onboarding
* ✅ **Persistence:** Snoozes survive app restarts and device reboots (including grouped messages)
* ✅ **Re-Fire Notifications:** Shows notification title + app name when snooze expires
* ✅ **Jump-Back:** ACTION_VIEW for shared URLs, contentIntent caching for conversation-level deep-linking
* ✅ **App Icons & Names:** Real app icons and names displayed in Active and Stash tabs
* ✅ **Notification Grouping:** Consistent grouping across Active/Dismissed/Stash matching Android's native behavior
* ✅ **MessagingStyle Support:** Individual messages extracted and displayed from grouped conversations (SMS, WhatsApp, etc.)
* ✅ **Non-MessagingStyle Grouping:** Apps like Blip that group without MessagingStyle show synthetic message list
* ✅ **Thread-Based Aggregation:** Uses shortcutId → groupKey → packageName fallback for conversation targeting
* ✅ **Re-Fire History:** Expired snoozes shown in History section with re-snooze/delete actions (7-day retention)
* ✅ **Dismissal Handling:** Recently Dismissed always shows individual notifications (group dismissals expand)
* ✅ **ContentIntent Caching:** Accurate jump-back to specific conversations (Discord, etc.)

### **Recent Improvements (Phase 2.5)**

#### **Re-Fire History**
- Added `SnoozeStatus` enum (ACTIVE/EXPIRED) to track snooze lifecycle
- Database migration v4→v5 adds status column
- History section in Stash tab shows expired snoozes
- Re-snooze from history creates new snooze, deletes old entry
- Auto-cleanup of history entries older than 7 days

#### **ContentIntent Jump-Back**
- `ContentIntentCache` singleton stores notification's original contentIntent
- When re-fire notification is tapped, uses cached intent for precise navigation
- Falls back to constructed intent if cache miss (app restart, etc.)

#### **Dismissal Handling (Like Native Notification History)**
- **Active tab**: Groups notifications by thread (mirrors system tray)
- **Recently Dismissed**: Always shows individual notifications
  - Swipe individual notification → that one shows in Recently Dismissed
  - Swipe collapsed group → expands into individual child notifications in Recently Dismissed
- Uses `FLAG_GROUP_SUMMARY` to detect group dismissals and expand them
- Recents buffer stores by notification `key` (not thread) for individual snoozing

### **Smart Filtering Decisions**
Based on testing, we filter out:
- **System packages:** Google Play Services, Search, Assistant, Play Store, Android Auto
- **OEM packages:** Samsung/Xiaomi/Huawei/LG system apps
- **Ongoing notifications:** Task reminders (TickTick), music players, timers - they have native snooze
- **Empty notifications:** No title/text/bigText
- **Auth/security:** 2FA, sign-in requests
- **Group summaries (from active list):** When children exist, filter out redundant summary

**Rationale:** Re-Fire focuses on **dismissible messaging notifications** where we can actually suppress during snooze. Ongoing notifications (like TickTick reminders) can't be dismissed programmatically, so users should use their native snooze functionality.

### **Next: Phase 3 - Intelligence**
- Message text logging for suppressed notifications
- Gemini Nano AICore integration
- Open Graph URL scraping for rich previews