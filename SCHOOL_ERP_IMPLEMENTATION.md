# VidyaOne School ERP expansion

This build expands the admissions application into a connected Nursery-to-Class-12 school ERP while preserving the original admissions workflow.

## Implemented modules

- **Principal / Management dashboard**: whole-school KPIs for active students, attendance, fee collection, outstanding dues, transport usage, published exams, class strength, attendance trend and fee collection by class.
- **Student Master**: class-filtered student directory, guardian/contact details, roll/admission numbers, house, blood group and new-student creation. CSV export is available.
- **Attendance**: date/class register, Present/Absent/Late/Half Day/Leave statuses, bulk save and daily summary metrics.
- **Fee Management**: billed/paid/outstanding KPIs, class filter, fee ledger, payment recording and downloadable CSV fee register.
- **Timetable**: weekly class timetable with period, subject, teacher, room and time. Teacher accounts automatically receive their assigned timetable; parents receive their child's class timetable.
- **Exams & Marks**: exam selector, class marksheet, subject maximum marks, teacher/admin score entry and automatic grade calculation.
- **Report Cards**: published result cards with totals/percentage, detailed subject breakdown, print/Save-as-PDF and downloadable standalone HTML copies.
- **Transport**: routes, vehicles, driver/attendant details, stops, pickup/drop times, monthly stop fee and student assignments.
- **Parent Portal**: child overview, attendance, fees, academic result, notices, timetable, transport, certificates and the existing admissions journey.
- **Teacher Portal**: today's schedule, assigned classes, attendance workflow, student directory, marks entry and report-card access.
- **Notifications**: per-user inbox, read state and management Send Notice action.
- **Certificates**: issued-document centre, management certificate issuance, printable certificate and downloadable standalone copy.
- **Admissions**: the original enquiry → application → admission workflow remains available and is integrated into management navigation.

## Demo accounts

All demo accounts use `Demo@1234`.

- `admin` — Admin + Principal/management access
- `principal` — Principal dashboard and school operations
- `teacher1` — Teacher portal
- `frontoffice` — Front office + student/transport/certificate views
- `accounts` — Admissions + fee management
- `meera` — Counsellor / admissions
- `committee` — Admission committee
- `parent` — Parent portal (linked to Aanya Sharma)

## Key API additions

Base path: `/api/v1/erp`

- `GET /dashboard`
- `GET|POST /students`
- `GET|POST /attendance`
- `GET /fees`
- `POST /fees/{id}/payment`
- `GET /timetable`
- `GET /exams`
- `GET|POST /marks`
- `GET /report-cards`
- `GET /transport`
- `GET /notifications`
- `POST /notifications`
- `POST /notifications/{id}/read`
- `GET /certificates`
- `POST /certificates`
- `GET /export/students`
- `GET /export/fees`

## Database

Liquibase migration `004-school-erp.sql` adds the ERP schema and demo records for students, attendance, fees, timetable, exams, marks, transport, notifications and certificates.

## Download / print behaviour

- Student Master: CSV
- Fee Register: CSV
- Admissions reports: CSV and print/Save-as-PDF
- Report cards: downloadable HTML and print/Save-as-PDF
- Certificates: downloadable HTML and print/Save-as-PDF

For a production rollout, server-generated signed PDFs can be added later with a dedicated PDF library/service and school branding/signatures.

## Validation performed

- `node --check` passes for both `app.js` and `api.js`.
- Java source was passed through `javac` syntax parsing; only expected missing-framework-class errors occur because Maven dependencies are unavailable in this execution environment.
- Full `mvn test` could not be run because Maven is not installed and the environment cannot resolve external hosts to download it.
