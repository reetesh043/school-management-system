# Email and Phone OTP Verification

VidyaOne now supports OTP verification of guardian email and mobile numbers from Student Master.

## User flow

1. Open Student Master and select a student.
2. Guardian email and phone show Verified or Verify with OTP.
3. Click Verify with OTP.
4. A six-digit OTP is generated, hashed in the database and delivered through the configured provider.
5. Enter the code. Successful verification timestamps the corresponding student contact.
6. Editing a verified contact automatically clears its verification state, so the new address/number must be verified again.

OTP rules: 6 digits, 5-minute expiry, 60-second resend cooldown, 5 attempts, single use. Indian 10-digit mobile numbers are automatically normalized to +91.

## Development mode

The default is `console` delivery for both channels. No external account is required. The backend logs the OTP and, because `otp.expose-dev-code=true`, the React dialog also shows the development OTP. Disable this outside local development.

## Real email through SMTP

Set environment variables before starting the backend:

```bash
export OTP_EMAIL_MODE=smtp
export OTP_EMAIL_FROM=no-reply@your-school.in
export SMTP_HOST=smtp.your-provider.com
export SMTP_PORT=587
export SMTP_USERNAME=your-user
export SMTP_PASSWORD=your-password
```

Any standard authenticated SMTP service can be used, including Google Workspace or Microsoft 365 when configured with an appropriate app/service credential.

## Real phone OTP through Twilio

```bash
export OTP_SMS_MODE=twilio
export TWILIO_ACCOUNT_SID=...
export TWILIO_AUTH_TOKEN=...
export TWILIO_FROM_NUMBER=+1...
```

The backend calls Twilio's Messages REST endpoint directly, so no Twilio Java SDK is required.

## APIs

- `POST /api/v1/otp/request` body `{ "studentId": 1, "channel": "EMAIL" }`
- `POST /api/v1/otp/verify` body `{ "challengeId": "...", "otp": "123456" }`
- `GET /api/v1/otp/status/{studentId}`

Request/verify are limited to ADMIN, PRINCIPAL and FRONT_OFFICE. OTP codes are never stored in plaintext.
