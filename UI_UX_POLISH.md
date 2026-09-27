# UI / UX Polish Pass

This cumulative build keeps existing ERP functionality and upgrades shared presentation patterns.

## Dialogs
- responsive desktop/mobile dialog sizing
- Escape key closes dialogs
- background scroll locking while a dialog is open
- sticky dialog headers and action bars
- clearer form spacing, hierarchy and focus states
- graceful backdrop fallback where blur is unsupported

## Data grids
- sticky headers
- zebra rows and stronger hover/focus feedback
- horizontal scroll treatment for wide school/ERP datasets
- record count footer and mobile scroll hint
- empty-state rows
- improved inline form controls and action buttons
- sticky student column in marks grids

## Reports / PDF
- dedicated print-ready report-card HTML
- dedicated landscape certificate HTML
- A4 page rules, print colour adjustment and page-break controls
- printable tables repeat table headers where supported
- report cards and certificates no longer depend on printing the live modal shell
- tested CSS choices avoid browser-specific layout dependencies and target Chrome, Edge and Firefox print engines

## Report Card
- redesigned student/exam header
- cleaner student metadata block
- enhanced subject/result table
- PASS / FAIL / incomplete chips
- totals, percentage, grade and result summary cards
- signature area

## Certificates
- premium framed certificate layout
- school crest treatment
- certificate number, title and student emphasis
- metadata chips and signature area
- A4 landscape print layout
