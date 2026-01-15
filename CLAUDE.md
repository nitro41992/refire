# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Re-Fire** is a Universal Focus Utility for Android that bridges active link sharing with passive notification management. It enables users to snooze specific conversations or content with precision, using on-device AI to summarize missed context upon return.

**Core Value Proposition:** Selective, conversation-level notification suppression with intelligent summarization—snooze your family group chat without silencing your boss.

## Architecture: Four Core Pillars

### A. Active Capture (Share Sheet)
- **System Share Sheet Integration:** `ACTION_SEND` intent filter captures URLs/text from any app
- **Open Graph Scraper:** Fetch metadata immediately with loading state in bottom sheet
- **Modal Bottom Sheet:** TickTick-style rapid time selection without leaving host app

### B. Reactive Capture (Snooze Tray)
- **NotificationListenerService:** Passive monitoring of status bar notifications
- **Recents Buffer:** Ephemeral in-memory queue of last 10 dismissed notifications (cleared on restart)
- **Quick Settings Tile:** One-tap snooze of recently dismissed items (shows toast if buffer empty)

### C. Focus Engine (The Mute)
- **Thread Identification:** Extract `ShortcutId` and `GroupKey` from notifications for conversation-level targeting
  - **Fallback Strategy:** Apps without thread IDs → package-level muting (entire app)
- **Silent Watchdog:** `cancelNotification()` to prevent vibration/sound from snoozed threads
- **Message Logging:** Background capture of suppressed notification text (deleted after re-fire)

### D. The Return (Re-Fire)
- **AI Summarization:** Gemini Nano (AICore) generates bulleted summary of missed messages
  - **Fallback:** Simple count notification ("You missed 20 messages from Valerie") when Gemini unavailable
- **Re-Fire Notification:** Count/summary text + "View in App" button for jump-back
- **Jump-Back Launching:** Use package name to open original app/conversation via intent

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room (for snooze persistence)
- **AI/ML:** Google Gemini Nano via AICore API
- **System Integration:**
  - `NotificationListenerService` (core notification interception)
  - `AlarmManager` (precise snooze scheduling)
  - Share Sheet (`ACTION_SEND` intent filter)
  - Quick Settings Tile
  - `BOOT_COMPLETED` receiver (restore snoozes after reboot)

## Implementation Phases

### Phase 1: Foundations - COMPLETE
- [x] Initialize Kotlin/Jetpack Compose Android project
- [x] Implement `NotificationListenerService` with `cancelNotification()` capability
- [x] Build Share Sheet intent filter + Modal Bottom Sheet UI
- [x] Permission flow onboarding for Notification Access

### Phase 1.5: In-App Management - COMPLETE
- [x] Active notifications list in app
- [x] Recently dismissed notifications (recents buffer)
- [x] Snooze from app (dismiss from system tray)
- [x] Stash tab for managing active snoozes (cancel/extend)
- [x] Thread identification with fallback strategy

### Phase 2: Persistence & Scheduling - COMPLETE
- [x] Room database schema for snooze storage (thread ID, end time, metadata, app name, messages)
- [x] Database migrations (v1→v2 appName, v2→v3 messages, v3→v4 shortcutId, v4→v5 status)
- [x] `AlarmManager` for reliable snooze scheduling with exact alarms
- [x] `BOOT_COMPLETED` receiver for persistence across reboots
- [x] Jump-back launching via ACTION_VIEW for URLs, package intents for notifications
- [x] POST_NOTIFICATIONS runtime permission (Android 13+)
- [x] Re-fire notifications posted when snooze expires
- [x] Smart notification filtering (system/OEM/ongoing notifications)
- [x] App name resolution via PackageManager
- [x] App icon display in Active and Stash cards
- [x] Notification grouping across all states (Active, Recently Dismissed, Stash)
- [x] MessagingStyle message extraction from grouped notifications
- [x] Synthetic message creation for non-MessagingStyle apps (Blip)
- [x] Thread-based aggregation using groupKey (prioritized over shortcutId)
- [x] Centralized message merging logic (NotificationGrouping.kt)
- [x] Recents buffer cleanup when snoozed

### Phase 2.5: History & Dismissal Handling - COMPLETE
- [x] Re-fire history with re-snooze capability (SnoozeStatus enum: ACTIVE/EXPIRED)
- [x] History section in Stash tab showing expired snoozes (24-hour retention)
- [x] Re-snooze and delete actions from history
- [x] ContentIntent caching for accurate conversation-level jump-back (Discord, etc.)
- [x] Recently Dismissed always shows individual notifications (like native notification history):
  - Swipe individual notification → that one shows in Recently Dismissed
  - Swipe collapsed group → breaks into individual notifications in Recently Dismissed
- [x] Filter group summary duplicates (FLAG_GROUP_SUMMARY detection)
- [x] Individual snoozing from Recently Dismissed (by notification key, not thread)
- [x] Rich re-fire notifications matching app card content:
  - Source app icon as large icon (prominent in notification body)
  - Re-Fire icon as small icon (status bar + header)
  - InboxStyle for multiple messages (up to 5 lines with "+X more" summary)
  - BigTextStyle fallback for single notification text
  - App name displayed in notification header via setSubText()

### Phase 3: Intelligence & Polish - NEXT
- Message text logging for suppressed notifications
- Gemini Nano AICore integration for summarization
- Open Graph scraper for URL previews
- Rich notification previews with images

### Phase 4: Optimization
- Quick Settings Tile for quick snooze access
- Battery optimization handling (request "Unrestricted" mode)
- Notification importance/priority handling
- Deep-link improvements for better jump-back

## Feature Priority Framework

| Priority | Feature | Rationale |
|----------|---------|-----------|
| **P0 (MVP Blockers)** | NotificationListenerService + kill command | Core technical foundation |
| **P0 (MVP Blockers)** | Share Sheet intake + Modal Bottom Sheet | Primary user entry point |
| **P1 (Core Value)** | Thread ID matching for conversation suppression | Key differentiator vs. app-wide muting |
| **P1 (Core Value)** | Jump-back launching | Completes user workflow loop |
| **P2 (Enhanced UX)** | Quick Settings Tile | Convenience feature |
| **P2 (Enhanced UX)** | Gemini Nano summaries | "Magic" layer (fallback available) |

## Architectural Decisions

### Data Persistence
- **Snoozes:** Persist in Room DB, survive reboots via `BOOT_COMPLETED` + `AlarmManager`
- **Message Text:** Delete immediately after re-fire (privacy-first approach)
- **Recents Buffer:** Ephemeral in-memory only (not persisted)

### Notification Lifecycle & Grouping

**States:**
- **Active**: Live notifications from system tray, grouped by thread
- **Dismissed**: User dismissed (24h retention), grouped by thread
- **Snoozed**: Active snooze, new messages suppressed & merged into snooze
- **History**: Expired snoozes (24h retention), partitioned by snooze cycle

**Merge Rules:**
| New Message Arrives | Thread Has... | Behavior |
|---------------------|---------------|----------|
| New notification | Nothing | New Active entry |
| New notification | Active items | Merge into Active (normal grouping) |
| New notification | Dismissed items (<4h old) | Pull Dismissed back to Active, merge messages |
| New notification | Dismissed items (>4h old) | Delete stale Dismissed, new Active entry (fresh lifecycle) |
| New notification | Snoozed items | Suppress notification, merge into Snoozed |
| New notification | History only | New Active entry (History stays separate) |

**Partition Rules:**
- **4-hour staleness**: Dismissed items >4h old are deleted (not merged) when new notification arrives
- **24-hour boundary**: Dismissed/History items older than 24h are auto-cleaned on snooze expiry
- **Snooze cycles**: Each expired snooze = separate History entry
- **Natural partition**: If Dismissed item is gone (stale or expired), new message starts fresh Active

**Ordering (all sections show most relevant at TOP):**
| Section | Sort Field | Direction | Rationale |
|---------|------------|-----------|-----------|
| Active | `postTime` | Descending | Most recent notifications first |
| Dismissed | `createdAt` | Descending | Most recently dismissed first |
| Snoozed | `snoozeEndTime` | Ascending | Expiring soonest first (urgency) |
| History | `snoozeEndTime` | Descending | Most recently expired first |

### Edge Case Handling
- **Duplicate Snoozes:** Latest snooze replaces previous one (simple override behavior)
- **Manual App Opening:** Snooze remains active if user opens app during snooze period
- **Uninstalled Apps:** Show summary without jump-back button at re-fire time
- **Missing Thread IDs:** Fall back to package-level muting (entire app suppression)

### Security & Privacy
- **No special handling for sensitive apps** (banking, 2FA, etc.)—user controls what they snooze
- Message text stored temporarily in Room DB (deleted post-summarization)

## Development Commands

**Project Status:** Phase 1 + 1.5 + 2 + 2.5 complete. Core snooze functionality fully working with persistence, alarms, and re-fire notifications. See `requirements.md` for detailed implementation status.

```bash
# Quick install via wireless ADB (recommended)
./scripts/connect.sh    # Connect to phone wirelessly (remembers last IP)
./scripts/install.sh    # Build, install, and launch app

# Standard gradle commands
./gradlew build         # Build the project
./gradlew test          # Run all tests
./gradlew installDebug  # Install debug APK on connected device
./gradlew lint          # Run lint checks
./gradlew clean         # Clean build
```

## ADB & Debugging

**ADB is globally available** (added to PATH via `~/.bashrc`)

```bash
# View Re-Fire logs (filter by tag)
adb logcat -s ReFireNotificationListener:* SnoozeAlarmReceiver:* IntentUtils:*

# Check cache hit/miss for jump-back navigation
adb logcat -s SnoozeAlarmReceiver:I | grep -E "CACHE (HIT|MISS)"

# Force kill app (to test cache miss scenario)
adb shell am force-stop com.narasimha.refire

# List installed packages (verify app visibility)
adb shell pm list packages | grep -i whatsapp
```

## Key Technical Challenges

### Solved (Phase 1-2.5)
1. ✅ **NotificationListenerService Reliability:** Successfully intercepts and cancels notifications with comprehensive filtering
2. ✅ **Thread ID Extraction:** Implemented 3-tier fallback (ShortcutId → GroupKey → PackageName) for conversation-level targeting
3. ✅ **AlarmManager Precision:** Using exact alarms with BOOT_COMPLETED receiver for reliability
4. ✅ **Permission UX:** Dual permission flow (Notification Access + POST_NOTIFICATIONS) with clear explanations
5. ✅ **Notification Filtering:** Smart filtering blocks system/OEM/ongoing notifications while allowing user apps
6. ✅ **App Name Resolution:** PackageManager-based approach for friendly app names
7. ✅ **Notification Grouping:** Consistent grouping across all states matching Android's native tray behavior
8. ✅ **MessagingStyle Handling:** Extract and display individual messages from grouped conversations
9. ✅ **Non-MessagingStyle Grouping:** Synthetic message creation for apps like Blip that group without MessagingStyle
10. ✅ **Jump-Back for Shared URLs:** ACTION_VIEW intents for proper deep-linking to shared content
11. ✅ **ContentIntent Caching:** Capture and reuse notification's contentIntent for accurate conversation jump-back (Discord, etc.)
12. ✅ **Group Summary Filtering:** Detect FLAG_GROUP_SUMMARY to prevent duplicate notification counts
13. ✅ **Dismissal Handling:** Recently Dismissed always shows individuals - group dismissals expand into child notifications
14. ✅ **Snooze Lifecycle Management:** SnoozeStatus (ACTIVE/EXPIRED) for history tracking with 24-hour auto-cleanup
15. ✅ **Rich Re-Fire Notifications:** Source app icon as large icon, InboxStyle/BigTextStyle for message content, app name in header

### Remaining (Phase 3-4)
1. **Gemini Nano Availability:** AICore API is device-dependent—graceful degradation to count-only notifications required
2. **Battery Optimization:** Need to handle doze mode and request unrestricted battery access
3. **Message Suppression Logging:** Capture text from notifications dismissed during active snooze period

## Known Limitations

### Jump-Back Navigation (Android Platform Limitation)
Deep-linking to specific conversations (WhatsApp chats, Discord channels) has fundamental Android limitations:

- **PendingIntent is opaque:** Cannot extract the underlying Intent from a notification's contentIntent
- **PendingIntent references lost on process death:** Android kills background processes unpredictably (memory pressure, no fixed timeout)
- **ShortcutManager only returns YOUR app's shortcuts:** Cannot query other apps' shortcuts
- **LauncherApps requires launcher permission:** Only default launchers can access other apps' shortcuts

**Current behavior:**
| Scenario | Result |
|----------|--------|
| Cache hit (process survived) | Deep-link works - opens exact conversation |
| Cache miss (process killed) | Falls back to app launcher - opens main view |

**Implementation:** `ContentIntentCache` stores PendingIntent in memory. `SnoozeAlarmReceiver` logs "CACHE HIT" or "CACHE MISS" to help debug.

## Database Schema

**Current version:** 7

| Migration | Change |
|-----------|--------|
| v1→v2 | Added `appName` column |
| v2→v3 | Added `messagesJson` column |
| v3→v4 | Added `shortcutId` column |
| v4→v5 | Added `status` column (ACTIVE/EXPIRED) |
| v5→v6 | Added `suppressedCount` column |
| v6→v7 | Added `contentIntentUri` column |

## Recent Changes (Reference)

### Jump-Back Simplification (Jan 2025)
- Removed `ShortcutLauncher.kt` - LauncherApps approach doesn't work without launcher permission
- Simplified `IntentUtils.kt` to 3-strategy fallback: URL → contentIntentUri → launcher
- Removed `QUERY_ALL_PACKAGES` permission (not needed)
- Kept `<queries>` block for package visibility (app icons on Android 11+)
- Added CACHE HIT/MISS logging in `SnoozeAlarmReceiver.kt`
