# Student Master operational fix

The Student Master is now a full CRUD/lifecycle module rather than a list-only screen.

## Backend APIs

- `GET /api/v1/erp/students?academicYear=2027-28&classCode=C1`
- `GET /api/v1/erp/students/{id}`
- `POST /api/v1/erp/students`
- `PUT /api/v1/erp/students/{id}`
- `PATCH /api/v1/erp/students/{id}/status`
- `DELETE /api/v1/erp/students/{id}`
- `GET /api/v1/erp/export/students?academicYear=2027-28`

Class filter codes are normalized before querying the database, so legacy values such as `CLASS_1` resolve to `C1`.

## React operations

- Load and browse students
- Search by student/admission/guardian details
- Filter by class and lifecycle status
- Add a student
- Open a student profile
- Edit student details and class
- Change ACTIVE / INACTIVE / WITHDRAWN / ALUMNI status
- Reactivate a non-active student
- Permanently delete a student (Admin/Principal only, with confirmation)
- Export Student Master CSV

Teacher access remains read-only. Front Office can create/edit/change lifecycle status. Admin/Principal have full operations.
