# Re-Fire Implementation Progress

## Current Status: Phase 1 Complete + Phase 1.5 Complete

---

## Completed Features

### Phase 1: Foundations

| Feature | Status | Notes |
|---------|--------|-------|
| Kotlin/Jetpack Compose project setup | Done | Min SDK 26, Target SDK 34 |
| NotificationListenerService | Done | Intercepts and cancels notifications |
| Share Sheet integration | Done | `ACTION_SEND` intent filter |
| Modal Bottom Sheet UI | Done | Time presets + custom picker |
| Permission flow | Done | Notification access onboarding |
| Material 3 theming | Done | Dynamic colors support |

### Phase 1.5: In-App Notification Management

| Feature | Status | Notes |
|---------|--------|-------|
| Active notifications list | Done | Real-time updates via StateFlow |
| Recently dismissed list | Done | Last 10 dismissed notifications |
| Snooze from app | Done | Dismisses from system tray |
| Stash tab | Done | View/cancel/extend active snoozes |
| Thread identification | Done | ShortcutId → GroupKey → PackageName fallback |

---

## Project Structure

```
app/src/main/kotlin/com/narasimha/refire/
├── data/model/
│   ├── NotificationInfo.kt      # Notification data model
│   ├── SharedContent.kt         # Share sheet content parsing
│   ├── SnoozePreset.kt          # Time preset logic
│   └── SnoozeRecord.kt          # Active snooze tracking
├── service/
│   └── ReFireNotificationListener.kt  # Core notification service
├── ui/
│   ├── MainActivity.kt
│   ├── ShareReceiverActivity.kt # Share sheet entry point
│   ├── components/
│   │   ├── ContentPreview.kt    # URL/text preview in bottom sheet
│   │   ├── CustomTimePicker.kt  # Date/time picker dialog
│   │   ├── NotificationCard.kt  # Notification display card
│   │   ├── SnoozeBottomSheet.kt # Time selection bottom sheet
│   │   ├── SnoozeRecordCard.kt  # Stash item card
│   │   └── TimePresetGrid.kt    # Preset time buttons
│   ├── screens/
│   │   ├── HomeScreen.kt        # Main tabbed interface
│   │   └── PermissionScreen.kt  # Notification access setup
│   └── theme/
│       └── Theme.kt             # Material 3 theming
└── res/values/
    └── strings.xml              # All UI strings
```

---

## Remaining Work

### Phase 2: Conversation Logic
- [ ] Room database for snooze persistence
- [ ] AlarmManager for snooze scheduling
- [ ] BOOT_COMPLETED receiver (restore snoozes after reboot)
- [ ] Jump-back launching (open source app)

### Phase 3: Intelligence & Polish
- [ ] Message text logging for suppressed notifications
- [ ] Gemini Nano AICore integration
- [ ] Open Graph scraper for URLs

### Phase 4: Optimization
- [ ] Quick Settings Tile
- [ ] Battery optimization handling
- [ ] Re-fire notification generation

---

## Key Files

| File | Purpose |
|------|---------|
| `ReFireNotificationListener.kt` | Core service - notification interception, snooze management |
| `HomeScreen.kt` | Main UI - Active/Stash tabs |
| `SnoozeBottomSheet.kt` | Time selection UI |
| `NotificationInfo.kt` | Notification data extraction |
| `SnoozeRecord.kt` | Snooze state tracking |

---

## Testing Notes

### Manual Testing Checklist
- [x] Grant notification access permission
- [x] Receive notifications → appear in Active tab
- [x] Swipe away notifications → appear in "Recently Dismissed"
- [x] Snooze from Active tab → moves to Stash
- [x] Cancel snooze → removes from Stash
- [x] Extend snooze → updates end time
- [x] Share URL/text → bottom sheet appears
- [x] Share sheet snooze → appears in Stash with "Shared" badge

### Known Limitations
- Snoozes are in-memory only (not persisted across app restarts)
- No re-fire notification yet (snoozes just expire silently)
- No Open Graph scraping for URLs yet
