<div align="center">

# KD Training

**A personal trainer's website and client space — a business card for visitors, a private training plan and a direct line to the coach for every client.**

Polish-language application · mobile-first

</div>

---

## What it is

One application with three roles:

- **Business card** — who the coach is, what he offers, how to get in touch.
- **Client space** — after logging in, each client sees their own plan: upcoming and past sessions, materials prepared for them (tips, photos, exercise videos), goals, and a conversation with the coach on every entry.
- **Coach's panel** — the coach plans sessions for each client, shares materials with selected people, manages client accounts, and keeps private notes nobody else can read.

## Who it's for

| Audience | What they get |
|----------|---------------|
| **Visitor** | A clear picture of the coach and the services, and a quick way to make contact. |
| **Client** | A private plan from the coach: a shared calendar, ticking sessions off with an effort rating, goals, exercise videos, and a comment thread with photos. |
| **Coach** | One place to plan every client's training, answer questions, reuse a video library and session templates, and see each client's progress. |

## Key features

- **Shared 1-on-1 calendar** — week and month views, sessions with or without a set time, tasks (e.g. a daily calorie limit) ticked off separately from sessions, copy and paste between days and between clients.
- **Conversation per session** — comments from both sides, photo attachments (e.g. a watch screenshot) that are private and deleted automatically after 30 days, unread markers in both directions.
- **Materials** — an exercise-video library (unlisted YouTube) and reusable session templates.
- **Goals on three horizons** and a trophy case of achieved ones.
- **Statistics** — streaks, attendance, monthly counts, effort trends, and an overtraining signal visible to the coach only.
- **Private coach notes** per session, readable by their author alone.

## Security and privacy

- **JWT** authentication (short-lived access token plus refresh), email verification, password reset, account lockout after repeated failed logins, breached-password check.
- **Rate limiting** per IP address in separate budgets (authentication, uploads, account, client plan, admin, public).
- The client plan holds **health data** and sits behind its own explicit consent (GDPR art. 9); until it is given, the whole plan stays closed.
- The plan area is **invisible to anyone without it** — the server answers as if the feature did not exist.
- **GDPR tools for the coach:** a full data export (ZIP) per person, erasure of a client's plan, account deletion.
- Encrypted nightly off-site backups, monitored for silence.

## Under the hood (in brief)

```
kd-training-backend/    # Java 25 / Spring Boot 4 — REST API, PostgreSQL (Flyway), security, email
kd-training-frontend/   # React 19 / TypeScript / Vite — site, client space, coach's panel
kd-training-hub/        # Docker Compose (dev / prod), backups, restore guide
```

CI builds and tests both parts, scans the images, and publishes them to GHCR; deployment is a manual workflow.

---

<div align="center">

**KD Training** · [latest release](../../releases)

</div>
