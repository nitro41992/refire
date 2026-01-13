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

### Phase 2: Conversation Logic - NEXT
- Room database schema for snooze storage (thread ID, end time, metadata)
- `AlarmManager` for reliable snooze scheduling
- `BOOT_COMPLETED` receiver for persistence
- Jump-back launching via `<queries>` manifest entry

### Phase 3: Intelligence & Polish
- Message text logging for suppressed notifications
- Gemini Nano AICore integration for summarization
- Open Graph scraper for URL previews

### Phase 4: Optimization
- Quick Settings Tile
- Battery optimization handling (request "Unrestricted" mode)
- Re-fire notification generation

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

### Edge Case Handling
- **Duplicate Snoozes:** Latest snooze replaces previous one (simple override behavior)
- **Manual App Opening:** Snooze remains active if user opens app during snooze period
- **Uninstalled Apps:** Show summary without jump-back button at re-fire time
- **Missing Thread IDs:** Fall back to package-level muting (entire app suppression)

### Security & Privacy
- **No special handling for sensitive apps** (banking, 2FA, etc.)—user controls what they snooze
- Message text stored temporarily in Room DB (deleted post-summarization)

## Development Commands

**Project Status:** Phase 1 + 1.5 complete. See `progress.md` for detailed implementation status.

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests <TestClassName>

# Install debug APK on connected device
./gradlew installDebug

# Run lint checks
./gradlew lint

# Clean build
./gradlew clean
```

## Key Technical Challenges

1. **NotificationListenerService Reliability:** Must consistently intercept and cancel notifications without user-visible lag
2. **Thread ID Extraction:** Not all apps expose `ShortcutId`/`GroupKey`—requires robust fallback logic
3. **Gemini Nano Availability:** AICore API is device-dependent—graceful degradation to count-only notifications required
4. **AlarmManager Precision:** Ensure alarms survive doze mode, app force-stop, and device reboots
5. **Permission UX:** Notification Access is sensitive—design clear onboarding flow explaining why it's needed
