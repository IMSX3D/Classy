# Two-state course dimming — 2026-10-14

User decision: normal and dimmed only. The prior 72% ended / 45% holiday split is superseded.

## Implementation

- `CourseCompletion` remains responsible only for dated occurrence completion: past date, or today's actual end time reached. Unknown/invalid same-day time does not infer completion.
- `CourseDimming` combines completion and holiday with OR, and owns a single dim opacity (45%). Multiple causes never multiply it.
- App grid/list use one outer layer. Conflict cards keep opaque surfaces, but resolve original background and original foreground separately against the same external backdrop; foreground is no longer blended over the already dimmed surface.
- Today uses the existing loaded holiday snapshot. Native weekly/today/two-day widget rendering reads local holiday data per date and preserves explicit transfer exemptions. No network is added to drawing.
- Weekly bitmap rendering applies one saved layer around each course. Actual RemoteViews and bitmap paths are both covered in the source changes.
- Normal title/metadata hierarchy and original course hue remain; “same dimming” means same treatment, not replacing all course colors with one gray.
- Holiday workday/transfer exemptions apply only to the holiday reason: completion still dims these classes after they end. Weekend alone remains normal.
- Existing optional holiday strike-through is not a separate opacity level; this batch does not change its setting semantics.

## Validation

- Final serial build succeeded: Debug JVM tests, Preview, Debug and AndroidTest. 2358 JVM tests passed; emulator `CourseReadingTest` passed all 4 tests, including pixel-identical completion-only / holiday-only / combined cards in light/dark and bar/fill.
- The first compile attempt exposed a missing stateless TodayContent parameter and was corrected. A subsequent Java build process ended without a final result; no root cause is claimed. A fresh no-daemon, one-worker build succeeded. Logs: `E:/C015/99-整理记录/dimming-two-state-retry.log` and `dimming-two-state-instrumentation.txt`.
- Vivo V2507A (Android 16) received the optimized Preview package by in-place upgrade. Existing courses and widget bindings were preserved. Phone initially showed dark week 5; before/after captures confirm ordinary completed courses and completed holiday courses now have the same title strength. Solid title pixels changed from RGB (195,195,196) versus (138,138,139) to (138,138,139) for both; light mode is (141,141,141) for both. This compares title ink, not colored course indicators or antialiased edges.
- Phone week 7 checked in light/dark: ended courses dim, future courses remain normal. Four manually installed desktop widgets checked with current dates: completed rows dim; tomorrow/future rows stay normal. Raw captures are outside Git under `E:/C015/99-整理记录/phone-final/dimming-*`. Restore phone to original dark theme / week 5; other saved display preferences unchanged.
- Physical tablet, widget holiday-date screenshots and all conflict/background combinations remain unverified. Emulator pixels validate the ordinary course component; they do not substitute for every platform rendering path. The large Today widget still scrolls/clips its list at the host viewport; this batch does not claim a layout redesign.
- APK: `E:/C015/交付/Classy-统一灰显-20261014/Classy-preview-arm64.apk`; SHA256 `D3B58FF621A422021409DF67C40964148E43F424DAFAE154CCC9790AF6F3C8C1`. Local source commit only; no push, tag or Release changes.
