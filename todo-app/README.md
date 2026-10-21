# Todo — Android app

A simple, private to-do app with deadlines and reminders. Native Android in Kotlin, using only
framework APIs (no third-party libraries), so the APK is tiny.

## Features (v1.3)
- **Quick add in plain language** — “Call mom tomorrow 6pm”, “Pay rent every month on 1 nov !”,
  “Standup weekdays 9:30am #Work”. Dates, times, repeats, `!` priority and `#list` are detected as you type.
- **Deadlines and reminders** — at the deadline or 15 min / 1 hour / 1 day before; notifications have
  **Mark done** and **Snooze 10 min**, and an in-app banner shows while the app is open
- **Repeating tasks** — daily, weekdays, weekly, monthly; completing one schedules the next
- **Lists and priority** — colour-coded lists (long-press a list chip to edit), high-priority flag
- **Today view, search**, Overdue / Today / Upcoming / Completed sections
- **Swipe** right to complete, left to delete, with **undo**
- **Home screen widget** — tap a task to complete it, + to add one
- **Daily summary** notification (default 9 PM, configurable)
- **Backup & restore** to a JSON file, plus Android auto-backup
- Light and dark themes, edge-to-edge on Android 15+

## Download
Every push that changes `todo-app/` runs tests and lint, builds the APK and attaches it to the
[`todo-latest` release](https://github.com/Andy7204/Andy7204.github.io/releases/tag/todo-latest).

## Project layout
| Path | What |
|---|---|
| `app/src/main/java/com/andy/todo/` | App code (`MainActivity`, `TaskSheet`, `Db`, `Reminders`, `TodoWidget`, `QuickAddParser`, …) |
| `app/src/test/` | Unit tests for the quick-add parser and repeat rules |
| `play-store/` | Store listing text, graphics, and the publishing guide |
| `privacy-policy.html` | Privacy policy (served by GitHub Pages) |
| `scripts/create-upload-key.sh` | Creates the private Play upload key |

## Build locally
Requires JDK 17 and the Android SDK (platform 36):

```sh
cd todo-app
./gradlew testReleaseUnitTest       # unit tests
./gradlew assembleRelease           # APK, signed with the public sideload key
```

For Play Store releases see [`play-store/PUBLISHING.md`](play-store/PUBLISHING.md).
