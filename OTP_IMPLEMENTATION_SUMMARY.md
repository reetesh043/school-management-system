# OTP Implementation Summary

Added reusable email and phone OTP verification with a Student Master integration.

- 6-digit cryptographically random OTP
- BCrypt hashing at rest
- 5-minute expiry
- 60-second resend cooldown
- 5-attempt limit
- single-use challenges
- email verification through console or SMTP
- SMS verification through console or Twilio REST
- Indian 10-digit numbers normalize to +91
- verified timestamps stored on the student guardian contact
- editing a verified phone/email invalidates its verification state
- Student Master displays verified/not-verified status and an OTP dialog

See `OTP_VERIFICATION.md` for provider setup and API details.
