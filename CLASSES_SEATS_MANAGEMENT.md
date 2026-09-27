# Classes & Seats Management

The Classes & Seats module is now configurable for academic year 2027-28.

## Operations
- View configured classes and live seat utilisation.
- Search by class name or class code.
- Add a new class with class code, display name, age range, capacity, admission fee, pass mark and fee due days.
- Edit an existing class and seat capacity.
- Delete an unused/unreferenced class.
- Capacity cannot be reduced below seats already in use.
- Duplicate class codes are rejected per academic year.

## Live class catalogue
Shared class selectors now load the class catalogue from `/api/v1/classes`, with Nursery-Class 12 as a fallback if the API is unavailable during initial rendering. Newly created classes therefore become selectable throughout screens using the shared ClassSelect component.

## APIs
- `GET /api/v1/classes?academicYear=2027-28`
- `POST /api/v1/classes`
- `PUT /api/v1/classes/{id}`
- `DELETE /api/v1/classes/{id}`

Write operations require Admin or Principal role.
