-- Do not equate Buffer Stuffing with application deadline misses.
SELECT jank_type, count(*) AS frames
FROM actual_frame_timeline_slice JOIN process USING (upid)
WHERE process.name = 'com.imsx3d.classy.benchmark'
GROUP BY jank_type;
