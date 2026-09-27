# Fee Management Fix

This cumulative update hardens and completes the Fee Management module.

## Backend
- Null-safe fee reads for amount and paid amount
- Class-code normalization on fee filtering
- Correct outstanding calculation for waived fees
- Overdue and waived summary counts
- Create fee due
- Edit fee due
- Record payment with overpayment validation
- Waive unpaid fee
- Delete unpaid fee
- Safe missing fee handling

## React UI
- Fee dashboard metrics
- Search by student/admission/fee/term/reference
- Filter by class and status
- Add fee
- Edit fee
- Record payment
- Waive fee
- Delete unpaid fee
- CSV export
- Refresh from persisted backend data after every mutation
