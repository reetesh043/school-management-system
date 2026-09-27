# Exams & Marks Workflow Enhancement

This cumulative build adds a complete exam setup and marks workflow.

## Added
- Create exams for an academic year with term, date range and status.
- Display saved exams with status, dates, configured class/subject counts and saved mark count.
- Configure exam subjects per class with exam date, maximum marks and pass marks.
- Update configured subjects and remove unused subjects (subjects with saved marks are protected from deletion).
- Enter and save marks for students.
- Reload persisted marks immediately after save and display stored mark + computed grade.
- Validation for exam dates, marks range, max/pass marks and invalid class/exam IDs.

## Backend fixes
- Fixed persisted mark retrieval by binding `examId` and `classId` to the student-mark query.
- Added safe subject lookup during mark save.
- Existing attendance, class-code, empty-result and timetable fixes are retained.
