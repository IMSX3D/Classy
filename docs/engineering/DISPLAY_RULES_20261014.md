# Course display and holiday rules — 2026-10-14

> 历史记录：本文写于 2026-10-14，记录当时的验收过程。"本机证据目录""交付目录"是开发者本机路径，不随仓库分发；结论与判据仍有效。

Status: implemented; scoped acceptance below. Existing release/tag unchanged.

## Decisions

- Ordinary Saturday/Sunday is not a cancellation signal. Remove weekend-dimming and makeup-exemption controls; old persisted keys remain for rollback but no longer decide rendering.
- Holiday dimming is an independent visual preference in Schedule display. Explicit makeup destination and workday take precedence. National workday data does not infer which school courses to move.
- Holiday decoration does not remove course records or disable reminders. The UI states this explicitly. School exceptions remain editable in holiday ranges/transfer settings.
- Ended classes retain 72% contrast; holiday decoration retains the existing 45%. Neither alone proves cancellation. Strike-through applies only to holiday decoration.
- Ordinary bar grid cells have no individual surface/border. Restore width by removing nested horizontal insets, retain a 3dp indicator and 2dp text gap. Filled and overlapping conflict cards keep their necessary surfaces; no transparent overlapping text layers.
- App grid and weekly desktop grid have independent Top/Center preferences, default Top. Centering excludes bottom metadata. Daily/two-day list layouts stay top-down.
- Hidden timetable name collapses the entire two-day header row; dates move to day headings. Corner safety inset remains.
- Widget names: 今日·小 / 今日 / 两日 / 本周 (Today · Small / Today / Two Days / This Week). In-app catalog order comes from metadata, not label sorting. Launcher ordering remains launcher-controlled.

## Display settings source audit

| Control | Meaning and action |
| --- | --- |
| Initial view | Startup preference; does not override an active session's chosen view. Retained. |
| Period/time labels | Moved into weekly-list group: grid already shows both. Fixed first-composition-only read and missing change emission. |
| Grid title position | New independent app-grid choice; live observable preference. |
| Room/teacher/none | Retained; reactive read fixed. Clarify selected item is preferred; tall cells can also show secondary detail. |
| Automatic/manual grid size | Retained; scale shown only in manual mode. Existing full-term slot policy preserved. |
| Weekly-list scale/two columns/hide empty | Retained within weekly-view group; observable values now invalidate consumers directly rather than an unread counter. |
| Visible weekdays | Retained; fixed cached read/change emission. At least one day required, hiding never deletes data. |
| Holiday display/style | Moved to parent; hide style choice when holiday display is off. Holiday page now focuses on dates/transfers. |
| Empty evening cutoff | Retained in experimental group. Applies only if the entire term has no evening course, as existing description says. |

## Regression gates

- Pure date logic: ordinary weekend, holiday weekend, workday, explicit transfer, legacy flags, disabled holiday decoration.
- Immediate setting observation: move title without re-entering; metadata stays anchored; widget choice unaffected.
- Narrow phone light/dark, long titles, short blocks, two-day header hidden/shown, preserved data/widget bindings.
- Phone/tablet source shared; disconnected tablet cannot be claimed physically retested.

## Evidence

- JVM: 2356 tests, zero failures/errors/skips (`bar-holiday-final-build.log`, test XML).
- Preview, Debug, AndroidTest assembled successfully (`bar-holiday-package.log`). Initial concurrent build lost its daemon; serial/in-process retry succeeded. A new test fixture initially missed required `color`; corrected before tests.
- Emulator: CourseReadingTest 3/3 passed (`bar-reading-final.log`). First alignment assertion used merged clickable-card bounds and failed; corrected to unmerged text bounds, then verified actual title movement, anchored metadata and independent widget preference. This is a corrected test, not a hidden app regression.
- vivo V2507A: Preview installed over existing test identity; original timetable and four manually placed widgets retained. Captured bar grid light/top, light/center and dark/center. Names regain two Chinese characters per row instead of one in this fixture. Long names can still truncate; full-name guarantee is not claimed.
- Phone: holiday toggle moved to parent; disabling hides style selector; restored on. Holiday date page no longer contains the three former switches. Widget management shows all four short names.
- Phone home: hidden-name two-day header now reads Today + date / Tomorrow + date without a standalone blank row. Weekly grid and daily widgets remain visible.
- Active weekly RemoteViews gravity/background fix built successfully (`bar-widget-active-build.log`), final Preview reinstalled. Phone home confirms centered titles above anchored rooms; switched back to Top and confirmed refresh. App title stayed Top independently. Both live and bitmap paths now implement the preference.
- Evidence remains outside Git: `本机证据目录/phone-final/bar-*`, build/test logs in its parent.
- Not physically revalidated this batch: tablet, all school holiday/transfer scenarios, every display-setting combination. Date logic and legacy-flag combinations have JVM coverage; source audit does not claim complete device acceptance.

Final phone state: light theme, app/grid-widget titles Top, holiday decoration restored on, table names remain hidden as before testing. No course/reminder edits. Delivery: `交付目录/Classy-色条与灰显整改-20261014/Classy-preview-arm64.apk`.

APK SHA-256: `15194b28931faf6f52a37250f83e8c9d2853702e94c76fbbdccc9a19bcd896c1`.
