# Report Card Enhancement

The report-card API and React view now return/render complete exam-level student report cards.

## Included student details
- Student name
- Admission number
- Roll number
- Class
- Date of birth
- Gender
- House
- Blood group
- Guardian name
- Academic year

## Included exam details
- Exam name
- Term
- Exam start/end dates in the API
- Published exam status

## Subject marks
Every subject configured for the student's class in the selected published exam is included, even when marks have not yet been entered.

Each subject shows:
- Subject name
- Exam date
- Maximum marks
- Pass marks
- Marks obtained
- Grade
- Pass/fail/not marked status
- Remarks

## Summary
- Total obtained marks
- Total maximum marks
- Percentage
- Overall grade
- PASS / FAIL / INCOMPLETE result
- Number of marked subjects vs configured subjects

The React screen also supports filtering by published exam, viewing the full report card, printing/saving as PDF, and downloading the complete report card as HTML.
