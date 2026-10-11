# Classy UI and motion

- Read `docs/design/DESIGN_SYSTEM.md` before UI or animation changes.
- Tablet and resizable-window support is a confirmed requirement. Read `docs/design/TABLET_ADAPTATION.md` for adaptive layout/state rules; include phone, tablet and split-window checks from the sample-page stage onward.
- The user's design baseline is “简洁、美观、统一、成一套完整的体系”. Add effects only when they clarify content, interaction feedback, or hierarchy. Evolve shared components together instead of creating page-specific visual systems.
- Maintain simplified Chinese and English. Normalize legacy language preferences through `SupportedLanguages`; keep release notes and build validation aligned with supported languages.
- Course indicators use the shared `CourseColorUtil.softenedIndicator` treatment. Keep readable text separate from decorative color and preserve saved custom colors.
- Glass is currently a researched optional direction, not an implemented app setting. Do not claim a translucent surface is liquid glass or a browser mockup establishes Android performance.
- Keep the existing release/tag unchanged unless the user requests a release. Discuss merge conflicts before resolving them.

- Initial UI state must represent the intended destination on first layout: distinguish loading from empty/missing, await required edit data before composing editable fields, and use initial list/pager positions rather than post-entry scrolling. Restored user position takes precedence over initial targeting. Check slow loading, non-first targets and recreation when changing these paths.
- Grouped selection rows must paint to the shared clipped card edges; put content padding inside rows, not around the row group. Independent pills/segmented tracks may retain intentional symmetric insets. Check first/middle/last selection and theme contrast.

- User baseline: ALL page transitions must be smooth and continuous, including enter/back/save exits, full-screen overlays and separate Activities. Preserve outgoing render data until disposal; do not reset forms before their exit animation. Let navigation own clean predictive back; intercept only unsaved/busy/cleanup states. Audit every new entry and exit, including cancellation, rapid input, reduced motion and phone/tablet windows. Build success alone is not visual acceptance.

- Keep docs/design/INTERACTION_STAGE1_CLOSURE.md current when adding routes or changing action semantics. Dialogs with Confirm/Cancel must keep local drafts and write only on confirmation; external widget navigation must not pop draft-owning hosts. Loading a different source must never fall back to another source’s data. Keep failed save/delete confirmations actionable, with all dismissal paths guarded while busy. Device acceptance remains separate.
