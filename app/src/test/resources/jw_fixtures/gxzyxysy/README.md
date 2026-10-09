# Sainz captured page

`full-20261009.html`: live read-only timetable query using the tester-authorized login
on 2026-10-09, term 20261 and the same class query as the original capture. Requested
100 rows per page: server returned all 25 rows on one page. This verifies complete
source data, not the Android three-page collector. Kept only table, term and paging;
replaced course row IDs and teacher names and cleared trailing ancillary columns.
No account credentials, session tokens, student details or raw response are retained.
The new tests cover 1–3 + 4 merging, 6–7 + 8–9 merging and Monday evening odd/even weeks.

Source: user-provided Android diagnostic capture on 2026-10-08.
Only `frames/3-main_main.html` was retained. Teacher names and the class/major
identifier were replaced; cookies, storage, navigation and network logs were not copied.
The screenshot and HTML both identify page 1 of 3, 25 total rows; this page contains 10.

The tests deliberately reject the original page as incomplete. One-page and second-page
variants created by tests are synthetic, not evidence of real school pagination success.
Evening node mapping must be confirmed by the user; the screenshot supplied separately
shows nodes 10–13 in the evening but disagrees with the HTML for one course placement.
