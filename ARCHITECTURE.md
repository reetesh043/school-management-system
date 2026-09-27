# Multi-module architecture

## Backend module

`backend` owns domain rules, persistence, workflow guards, payment simulation, reporting, security and all REST controllers. It contains no React build output and no legacy application JavaScript.

## UI module

`ui` is a standalone React application. `src/api.js` is the API boundary and is responsible for authentication headers, JSON requests, multipart upload, report downloads and error normalization.

The UI uses role-aware navigation, but authorization is still enforced by Spring Security at the API layer.

## Local request flow

```text
Browser :5173
   |
   | /api/v1/...
   v
Vite dev proxy
   |
   v
Spring Boot :8080
   |
   v
Services / repositories / H2
```

## Production request flow

The UI can be hosted as static files on a web server/CDN and configured with `VITE_API_BASE_URL` to call the Spring Boot API. The backend currently permits the Vite local origin and can be extended to use environment-driven production origins when deployment domains are known.

## Why the modules stay separate

This keeps frontend and backend release concerns independent, makes the REST contract explicit, allows React to evolve without coupling to Spring MVC templates/static resources, and keeps the backend reusable for mobile apps or other clients later.
