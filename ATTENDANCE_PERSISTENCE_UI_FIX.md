# Attendance saved-data display fix

The Attendance screen now differentiates persisted attendance from unmarked students.

Changes:
- Attendance API returns `marked`, `unmarked`, `markedAt`, and `latestSavedDate`.
- Attendance percentage is based on students actually marked for that date, rather than treating unmarked students as absent.
- React no longer defaults missing attendance to `PRESENT`.
- Unmarked rows display `Not marked`.
- The page shows the latest date with saved attendance for the selected class and provides a shortcut to open it.
- Saving attendance refetches the API immediately so the UI reflects persisted database state.
- `HALF_DAY` is now available in the attendance status selector, matching the database constraint.
