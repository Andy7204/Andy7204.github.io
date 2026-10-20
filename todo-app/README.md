# Todo — Android app

A simple to-do app with deadlines and reminders. Native Android (Java), no third-party libraries.

- Add a task with a title, a deadline (date + time) and a reminder (none / at deadline / 15 min / 1 hour / 1 day before)
- Tasks are grouped into **Overdue**, **Upcoming** and **Completed**, with colour-coded deadline chips and a progress card
- Reminders arrive as notifications with **Mark done** and **Snooze 10 min** buttons, and as an in-app banner while the app is open
- Reminders are rescheduled after a reboot; light and dark mode follow the system

## Download

Every push that changes `todo-app/` builds the APK on GitHub Actions and attaches it to the
[`todo-latest` release](https://github.com/Andy7204/Andy7204.github.io/releases/tag/todo-latest).
Open that page on your phone, download `Todo.apk` and allow "Install unknown apps" when asked.

## Build locally

Requires JDK 17 and the Android SDK (platform 34):

```sh
cd todo-app
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the committed `app/todo-release.jks` so new builds install over old ones.
