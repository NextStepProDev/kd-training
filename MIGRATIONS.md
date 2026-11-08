# Mocny Grzbiet — migracje bazy (Flyway)

> Wydzielone z `CLAUDE.md`. Uzasadnienia decyzji treningowych → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md).

## Stan

**Obecny stan: V6. Kolejna migracja: V7.**

| Wersja | Co dodaje |
|--------|-----------|
| V1 | schemat startowy: `users`, `auth_tokens`, `personal_trainings`, `training_comments`, `training_calendar_reads`, `training_deletions`, `athlete_goals`, `exercise_videos`, `training_templates`, `training_attachments`, `admin_private_notes` |
| V2 | `invitations`: rejestracja tylko z zaproszenia (adres, hash tokenu, ważność; jedno otwarte zaproszenie na adres; bez klucza do `users`) |
| V3 | `athlete_materials` (materiały na stałe dla jednej osoby: tytuł + film z biblioteki i/lub opis; `video_id` RESTRICT) i `material_questions` (wątek pytań pod materiałem) |
| V4 | `personal_trainings.series_id`: serie co tydzień (każdy termin to zwykły wiersz; kopie nie dostają `series_id`) |
| V5 | `coach_days_off`: dni wolne trenera (zakres dat włącznie + notatka widoczna dla wszystkich; bez klucza do `users`) |
| V6 | `users.blocked_at`: blokada konta po końcu współpracy (null = aktywne; dane zostają) |
| V7 | `training_change_notices`: kolejka maili o zmianach w planie (jeden wiersz na trening, pierwsza zmiana od ostatniego maila; bez FK do treningu, bo odwołanie przeżywa wiersz) |
| V8 | `personal_trainings.group_id`: trening wspólny — wiersz na osobę, wspólny identyfikator sesji (bez limitu osób, kopia go nie dostaje) |

## Jak powstała V1 (26.10.2026)

Fire Academy miała 42 migracje (V1–V42), z czego większość dotyczyła funkcji, których tu nie ma.
Zamiast przepisywać schemat z pamięci:

1. Wszystkie 42 migracje Fire puszczone po kolei na czystym `postgres:18-alpine`.
2. Usunięte tabele wyciętych funkcji: `events`, `event_types`, `event_type_photos`, `instructors`,
   `instructor_categories`, `enrollments`, `training_slots`, `training_enrollments`,
   `training_payments`, `training_refunds`, `training_holidays`, `training_cancelled_sessions`,
   `athlete_weights`.
3. Uproszczone dwie tabele, które sięgały do wyciętych:
   - `admin_private_notes` — zostaje tylko kotwica `training_id` (`NOT NULL`), znikają `event_id`,
     `slot_id`, `athlete_id`, `session_date`; częściowy unikalny indeks zamieniony na zwykłe
     `UNIQUE (author_id, training_id)`.
   - `athlete_goals` — bez `kind`, `target_weight_kg`, `start_weight_kg`, `achieved_automatically`;
     limit „jeden aktywny cel na horyzont” wraca do `(athlete_id, horizon)`.
4. `V1__initial_schema.sql` napisana ręcznie (czytelnie, z komentarzami z oryginałów) i
   **porównana z bazą z kroków 1–3**: kolumny z typami, długościami, nullowalnością i wartościami
   domyślnymi, każde ograniczenie (`pg_get_constraintdef`) i każdy indeks (`pg_indexes`) —
   wynik identyczny, łącznie z nazwami ograniczeń.

Starych plików migracji nie ma w repo — są w Fire
(`fire-academy-backend/src/main/resources/db/migration`).

## Zasady

- **Do pierwszego wdrożenia na produkcję `V1` wolno poprawiać** — nigdzie poza lokalnymi bazami
  jeszcze nie działa (profil `dev` i tak odtwarza bazę przy każdym starcie). Po poprawce V1 —
  reset lokalnej bazy: `docker compose -f docker-compose.dev.yml down -v`.
- **Od pierwszego wdrożenia migracje są niezmienne.** Zmiana choćby komentarza zmienia checksum i
  Flyway odmawia startu na produkcji. Każda zmiana schematu to nowy plik `V{n}__opis.sql`.
- Komentarze w nowych migracjach po angielsku.
- Nowa tabela z kluczem obcym do `users` → wpis do eksportu RODO albo do wykluczeń
  (`UserDataExportCoverageTest` to wymusza) i do `src/test/resources/cleanup.sql`.
- `ddl-auto: none` — Hibernate nie sprawdza schematu przy starcie. Rozjazd encji z migracją wychodzi
  dopiero na zapytaniu; testy integracyjne (Testcontainers, baza wyłącznie z migracji) przechodzą
  przez prawdziwe endpointy, więc tam go łapią.
