-- Inclusive slice durations overlap; do not add different categories together.
SELECT s.name, count(*) AS occurrences,
       round(max(s.dur) / 1e6, 2) AS max_ms,
       round(sum(s.dur) / 1e6, 2) AS inclusive_total_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'com.imsx3d.classy.benchmark'
  AND t.is_main_thread = 1 AND s.dur > 0
GROUP BY s.name ORDER BY inclusive_total_ms DESC LIMIT 30;
