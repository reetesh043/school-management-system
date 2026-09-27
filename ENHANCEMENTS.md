# Admissions product enhancement summary

This version upgrades the application from a basic admissions register into a more modern Nursery-to-Class-12 admissions operations product.

## UI and dashboard
- New admissions command centre with modern KPI cards and responsive layout.
- Operational attention cards for overdue follow-ups, payment pending, waitlist and lost enquiries.
- Application stage distribution and conversion progress visualisations without adding a frontend chart dependency.
- Seat pressure and class demand panels.
- Improved navigation, visual hierarchy, responsive behaviour and print styling.
- Role-aware guardian dashboard so parent users do not see staff analytics.

## Reporting
- New management overview API.
- Current-stage distribution report.
- Class demand / admission / capacity report.
- Source conversion reporting with conversion percentages.
- Class-specific funnel reporting instead of workflow-wide funnel counts.
- CSV downloads for management summary, class demand, seat capacity and source conversion.
- Print / Save PDF support from Dashboard and Reports using the browser print flow.
- Reporting now counts active held/confirmed seat allocations as used capacity.

## Indian school structure
- Default catalogue expanded to Nursery, LKG, UKG and Classes 1 through 12.
- Age bands, capacity and sample admission fees included for all levels.
- Required-document rules expanded for higher classes, including report cards and transfer certificates where applicable.

## Run
Use the existing project instructions:

```bash
mvn spring-boot:run
```

Then open `http://localhost:8080/`.

> Note: Maven was not installed in the editing environment, so the full Spring test suite could not be executed here. JavaScript syntax and source-level structural checks were run successfully.
