# Parent / Guardian Self Registration

VidyaOne now includes a self-service Parent / Guardian registration flow directly from the login page.

## Registration flow
1. Parent selects **New parent or guardian?** on the sign-in page.
2. Enters the student's admission number and date of birth.
3. Chooses guardian email or mobile for OTP delivery.
4. The backend matches an ACTIVE student and sends the OTP only to the contact already stored in Student Master.
5. Parent enters the 6-digit OTP, chooses a username and creates a password.
6. A new `app_user` is created with the `GUARDIAN` role and linked to `student.guardian_user_id`.
7. The UI automatically signs the new guardian into the existing Parent Portal.

## Security controls
- Admission number + DOB must match an active student.
- Registration is blocked if a guardian account is already linked.
- OTP inherits the existing 5-minute expiry, resend cooldown, single-use behavior and attempt limit.
- Password requires at least 8 characters, including a letter and a number.
- Usernames are checked for uniqueness.
- OTP is delivered only to the guardian email/phone already held by the school.

## Public APIs
- `POST /api/v1/public/parent-registration/request-otp`
- `POST /api/v1/public/parent-registration/complete`

These endpoints are under the existing `/api/v1/public/**` security allowance, while the rest of the Parent Portal remains authenticated.

## Local development
When OTP console mode is enabled, the development OTP can be displayed in the registration screen. For production, configure SMTP and/or Twilio as documented in `OTP_VERIFICATION.md`.
