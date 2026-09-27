# H2 Attendance Dashboard Fix

Fixed the management dashboard failure caused by an ambiguous `status` column in `ErpService.attendanceTrend()`.

## Root cause

The query joins `attendance_record ar` and `student s`, then referenced `status` without a table alias. H2 2.2.224 rejected the query as ambiguous.

## Fix

The attendance trend query now explicitly uses:

- `ar.status`
- `ar.attendance_on`
- `group by ar.attendance_on`
- `order by ar.attendance_on`

No business logic or UI behaviour was changed.
