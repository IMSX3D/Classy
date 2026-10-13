-- Benchmark-only business layout markers. The nested spans are inclusive.
SELECT s.name, count(*) AS calls,
       round(max(s.dur) / 1e6, 3) AS max_ms,
       round(sum(s.dur) / 1e6, 3) AS inclusive_total_ms
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING (utid) JOIN process p USING (upid)
WHERE p.name = 'com.imsx3d.classy.benchmark'
  AND t.is_main_thread = 1 AND s.name GLOB 'Classy/measure/*'
  AND s.dur > 0
GROUP BY s.name ORDER BY max_ms DESC;
