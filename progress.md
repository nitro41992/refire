# Re-Fire Implementation Progress

## Current Status: Phase 1 Complete + Phase 1.5 Complete + Phase 2 Complete

---

## Completed Features

### Phase 1: Foundations

| Feature | Status | Notes |
|---------|--------|-------|
| Kotlin/Jetpack Compose project setup | Done | Min SDK 26, Target SDK 34 |
| NotificationListenerService | Done | Intercepts and cancels notifications |
| Share Sheet integration | Done | `ACTION_SEND` intent filter |
| Modal Bottom Sheet UI | Done | Time presets + custom picker |
| Permission flow | Done | Notification access + POST_NOTIFICATIONS (Android 13+) |
| Material 3 theming | Done | Dynamic colors support |

### Phase 1.5: In-App Notification Management

| Feature | Status | Notes |
|---------|--------|-------|
| Active notifications list | Done | Real-time updates via StateFlow |
| Recently dismissed list | Done | Last 10 dismissed notifications |
| Snooze from app | Done | Dismisses from system tray |
| Stash tab | Done | View/cancel/extend active snoozes |
| Thread identification | Done | ShortcutId → GroupKey → PackageName fallback |

### Phase 2: Persistence & Scheduling

| Feature | Status | Notes |
|---------|--------|-------|
| Room database | Done | Schema v2 with appName field |
| Database migration | Done | v1→v2 migration for appName |
| AlarmManager integration | Done | Exact alarms for snooze expiration |
| BOOT_COMPLETED receiver | Done | Restore snoozes after reboot |
| Re-fire notifications | Done | Posted when snooze expires |
| Jump-back launching | Done | Opens source app on notification tap |
| POST_NOTIFICATIONS permission | Done | Runtime permission for Android 13+ |
| Smart notification filtering | Done | Blocks system/OEM/ongoing notifications |
| App name resolution | Done | PackageManager.getApplicationLabel() |
| App icon display | Done | Shows in Active and Stash cards |

---

## Project Structure

```
app/src/main/kotlin/com/narasimha/refire/
├── core/util/
│   └── AlarmManagerHelper.kt    # Exact alarm scheduling
├── data/
│   ├── database/
│   │   ├── ReFireDatabase.kt    # Room database singleton
│   │   ├── SnoozeDao.kt         # Database access object
│   │   └── SnoozeEntity.kt      # Room entity + converters
│   ├── model/
│   │   ├── NotificationInfo.kt  # Notification data model with app name/icon
│   │   ├── SharedContent.kt     # Share sheet content parsing
│   │   ├── SnoozePreset.kt      # Time preset logic
│   │   ├── SnoozeRecord.kt      # Active snooze tracking
│   │   └── SnoozeSource.kt      # Enum: NOTIFICATION | SHARE_SHEET
│   └── repository/
│       └── SnoozeRepository.kt  # Data layer abstraction
├── service/
│   ├── BootCompletedReceiver.kt        # Restore snoozes after reboot
│   ├── ReFireNotificationListener.kt   # Core notification service with filtering
│   └── SnoozeAlarmReceiver.kt          # Handle snooze expiration + re-fire
├── ui/
│   ├── MainActivity.kt          # Dual permission flow
│   ├── ShareReceiverActivity.kt # Share sheet entry point
│   ├── ReFire.kt                # Application class (channel creation)
│   ├── components/
│   │   ├── ContentPreview.kt    # URL/text preview in bottom sheet
│   │   ├── CustomTimePicker.kt  # Date/time picker dialog
│   │   ├── NotificationCard.kt  # Notification card with app icon
│   │   ├── SnoozeBottomSheet.kt # Time selection bottom sheet
│   │   ├── SnoozeRecordCard.kt  # Stash card with app icon/name
│   │   └── TimePresetGrid.kt    # Preset time buttons
│   ├── screens/
│   │   ├── HomeScreen.kt        # Main tabbed interface
│   │   └── PermissionScreen.kt  # Dual permission cards (access + POST)
│   └── theme/
│       └── Theme.kt             # Material 3 theming
└── res/values/
    └── strings.xml              # All UI strings (updated for dual permissions)
```

---

## Remaining Work

### Phase 3: Intelligence & Polish
- [ ] Message text logging for suppressed notifications
- [ ] Gemini Nano AICore integration
- [ ] Open Graph scraper for URLs
- [ ] Rich notification previews with images

### Phase 4: Optimization
- [ ] Quick Settings Tile
- [ ] Battery optimization handling (request "Unrestricted" mode)
- [ ] Notification importance/priority handling
- [ ] Deep-link improvements for better jump-back

---

## Key Files

| File | Purpose |
|------|---------|
| `ReFireNotificationListener.kt` | Core service - notification interception, filtering, snooze management |
| `SnoozeAlarmReceiver.kt` | Handles snooze expiration and posts re-fire notifications |
| `AlarmManagerHelper.kt` | Exact alarm scheduling and cancellation |
| `ReFireDatabase.kt` | Room database with migration support |
| `SnoozeRepository.kt` | Data layer abstraction for snooze operations |
| `HomeScreen.kt` | Main UI - Active/Stash tabs with real-time updates |
| `SnoozeBottomSheet.kt` | Time selection UI |
| `NotificationInfo.kt` | Notification data extraction with app name resolution |
| `SnoozeRecord.kt` | Snooze state tracking with expiration logic |
| `PermissionScreen.kt` | Dual permission onboarding UI |

---

## Testing Notes

### Manual Testing Checklist (Phase 1-2)
- [x] Grant notification access permission
- [x] Grant POST_NOTIFICATIONS permission (Android 13+)
- [x] Receive notifications → appear in Active tab with app icons
- [x] System notifications filtered (Google Play Services, etc.)
- [x] Ongoing notifications filtered (TickTick reminders, music players)
- [x] App names displayed correctly (not package names)
- [x] Swipe away notifications → appear in "Recently Dismissed"
- [x] Snooze from Active tab → dismisses from system tray, moves to Stash
- [x] Cancel snooze → removes from Stash
- [x] Extend snooze → updates end time and reschedules alarm
- [x] Share URL/text → bottom sheet appears
- [x] Share sheet snooze → appears in Stash with "Shared" badge
- [x] Wait for snooze expiration → re-fire notification appears
- [x] Tap re-fire notification → opens source app
- [x] App restart → snoozes persist from database
- [x] Device reboot → snoozes restored via BOOT_COMPLETED

### Known Limitations
- No message text logging yet (Phase 3)
- No AI summarization (Phase 3)
- No Open Graph scraping for URLs (Phase 3)
- Jump-back uses basic intent (could be improved with deep-linking)
- No Quick Settings Tile yet (Phase 4)
