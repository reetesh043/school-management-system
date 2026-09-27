# VidyaOne School ERP

VidyaOne is a Nursery to Class 12 school ERP for the Indian schooling model. The project is now split into two modules while preserving the existing admissions and ERP functionality.

## Project structure

```text
vidyaone-school-erp/
├── pom.xml                 # Maven aggregator
├── backend/                # Spring Boot REST API
│   ├── pom.xml
│   ├── src/main/java
│   ├── src/main/resources
│   ├── src/test
│   ├── docs
│   └── http
└── ui/                     # React + Vite UI
    ├── pom.xml
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src
        ├── api.js
        ├── main.jsx
        └── styles.css
```

The Spring Boot module no longer contains the legacy static HTML/JavaScript frontend. React is the only application UI.

## Technology

Backend:
- Java 21
- Spring Boot 3.3.5
- Spring MVC / REST
- Spring Security with role based access
- Spring Data JPA
- Liquibase
- H2 for the demo database
- Springdoc / Swagger

UI:
- React 19
- Vite 7
- Lucide React icons
- REST API client with Basic authentication for the demo
- Vite proxy for local API calls

## Running locally

### 1. Start the API

From `backend`:

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

### 2. Start React

From `ui`:

```bash
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

During local development Vite proxies `/api` requests to `http://localhost:8080`, so the React code uses the same relative API paths as before.

## Root Maven build

The root `pom.xml` aggregates both modules. The UI module uses `frontend-maven-plugin` to install Node/npm, install frontend packages and run the Vite production build.

```bash
mvn clean install
```

The React production output is created under:

```text
ui/dist/
```

The backend remains an API-only deployable JAR. This deliberately keeps the deployment units separate even though they live in one repository.

## Deploying the React UI separately

Set:

```bash
VITE_API_BASE_URL=https://api.your-school-domain.com
```

Then build:

```bash
npm run build
```

For local development this variable should normally remain blank because Vite's proxy is already configured.

## Functional areas retained in React

- Principal / Management dashboard
- Teacher dashboard
- Parent portal
- Student Master
- Attendance and bulk attendance save
- Fee register, payment posting and CSV export
- Timetable
- Exams and marks entry
- Published report cards and downloadable report card files
- Transport routes and student assignments
- Notifications and management notice creation
- Certificates, issue/view/download/print
- Admissions enquiries
- Enquiry assignment, follow-up, conversion and lost handling
- Admission application creation
- Dynamic admission form fields
- Admission workflow transitions and guard visibility
- Document upload, verify and reject
- Assessment score entry
- Invoice and demo payment capture
- Communication history
- Nursery through Class 12 class and seat view
- Admission management reports
- Class demand, source, stage and class-specific funnel reporting
- CSV report downloads
- Print / Save PDF reporting

## Demo accounts

All demo users use password `Demo@1234`.

| Username | Portal / Role |
|---|---|
| admin | Admin / Management |
| principal | Principal |
| teacher1 | Teacher |
| frontoffice | Front Office |
| meera | Admissions Counsellor |
| committee | Admission Committee |
| accounts | Accounts |
| parent | Guardian / Parent Portal |

## API boundary

The React UI only communicates with the backend through `/api/v1/**`. No UI business logic reads the database directly. Role enforcement remains on the backend through Spring Security, even when a React menu item is hidden for a role.

## Authentication note

HTTP Basic is retained because this project already used it and the requested migration does not change business functionality. For production, the recommended next security step is OIDC/OAuth2 with a proper identity provider and JWT resource server.
