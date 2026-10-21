# Google Play listing – Todo

Copy these into Play Console → **Grow → Store presence → Main store listing**.

## App name (max 30)
Todo – Tasks & Reminders

## Short description (max 80)
Simple to-do list with deadlines, smart reminders, repeating tasks and a widget.

## Full description (max 4000)
Todo is a calm, simple to-do list that makes sure you never miss a deadline.

TYPE IT THE WAY YOU SAY IT
Write “Call mom tomorrow 6pm” or “Pay rent every month on 1 nov !” and Todo fills in the
deadline, repeat, list and priority for you.

REMINDERS THAT ACTUALLY WORK
• Get reminded at the deadline, or 15 minutes, 1 hour or 1 day before
• Mark done or snooze right from the notification
• An optional daily summary tells you what's left for today

REPEATING TASKS
Daily, weekdays, weekly or monthly — complete one and the next is scheduled automatically.

STAY ORGANISED
• Colour-coded lists like Personal, Work and Shopping
• Flag what's important — high-priority tasks rise to the top
• Today view, search, and clear Overdue / Today / Upcoming sections

FAST AND FRIENDLY
• Swipe right to complete, left to delete — with undo
• Home screen widget: see what's due and tick tasks off without opening the app
• Beautiful light and dark themes

PRIVATE BY DESIGN
No account, no ads, no tracking. Your tasks stay on your phone. Back up to a file any time,
and restore on a new phone.

## Category
Productivity

## Tags
To-do list, Task manager, Reminders, Productivity

## Contact details
Email: (your support email – required by Play)
Website: https://andy7204.github.io/

## Privacy policy URL
https://andy7204.github.io/todo-app/privacy-policy.html
(Live once this branch is merged to `main` and GitHub Pages redeploys.)

## Graphics
| Asset | File | Notes |
|---|---|---|
| App icon 512×512 | `play-store/icon-512.png` | |
| Feature graphic 1024×500 | `play-store/feature-graphic.png` | |
| Phone screenshots (2–8) | take on your phone | Suggested: task list, add-task sheet with “Call mom tomorrow 6pm”, notification with Mark done, widget on home screen, dark mode |

---

# App content answers (Play Console → Policy → App content)

**Privacy policy:** URL above.

**Ads:** No, the app does not contain ads.

**App access:** All functionality is available without special access.

**Content rating questionnaire:** Category *Utility, Productivity, Communication, or Other*.
Answer **No** to every question (no violence, sexual content, gambling, user interaction,
sharing location, purchases). Expected rating: Everyone / PEGI 3.

**Target audience:** 13+ (choosing ages under 13 adds Families policy requirements).

**News app:** No.

**Data safety:**
- Does your app collect or share any of the required user data types? **No**
- Is all user data encrypted in transit? Not applicable (no data leaves the device) – answer **Yes**
  if forced to choose; nothing is transmitted.
- Do you provide a way for users to request that their data be deleted? **Yes** – delete in app or uninstall.
- Result: “No data collected · No data shared”.

**Exact alarm permission declaration:** Todo uses `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`).
The user grants it from a prompt in the app; reminders still work, slightly less precisely, without it.
If Play asks for a justification: “User-set task reminders that must fire at the time the user chose.”

**Health / financial / government features:** None.

---

# Release notes – 1.3.0
- Repeating tasks: daily, weekdays, weekly, monthly
- Type naturally: “Dentist friday 3pm #Personal !”
- Lists with colours, and high-priority flags
- Swipe to complete or delete, with undo
- Home screen widget
- Today view, search and a daily summary
- Backup and restore
