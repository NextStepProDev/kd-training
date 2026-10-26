# KD Training

Strona i aplikacja trenera personalnego **Kacpra Dąbrowskiego**: wizytówka + strefa podopiecznego
(plan 1:1, wiadomości, zdjęcia, filmy, cele) + panel trenera. Java/Spring Boot backend + React
frontend, Tailwind 4, czerń i biel. Wyłącznie język polski. Najpierw telefon (brief klienta).

**Pochodzenie:** fork **Fire Academy** (`Przemo/fire-academy`, commit `17142a0`, 26.10.2026) —
z czystą historią gita, bez funkcji klubu (wydarzenia, obozy, szkolenia, kadra, zapisy, treningi
grupowe z rozliczeniami, waga). Szczegóły i lista do zrobienia: sekcja „Fork z Fire Academy” niżej.
Komentarze w kodzie i w dokumentach mówiące „sibling app” / „bliźniaczy projekt” albo podające
pomiary z produkcji odnoszą się do Fire Academy i NSP — to wiedza odziedziczona, nie zmierzona tutaj.

**Domena:** jeszcze nieustalona — w repo zaślepka `kdtraining.example` (nginx, `.env.example`,
`robots.txt`, `prerender.mjs`, `AppConfig`). Deploy czyta domenę ze zmiennej repozytorium `SITE_URL`.
**Firma / administrator danych:** do uzupełnienia (polityka prywatności, eksport RODO).
**Mail kontaktowy:** zaślepka `kontakt@kdtraining.example` — `kd-training-frontend/src/config/site.ts`
(+ `withdrawNote` w `locales/pl/calendar.json`, `email.*` w `messages.properties`).
**Wersja:** odczytywana z pliku `VERSION` via `@version@` placeholder w `application.yml`

---
## Mapa dokumentacji

Ten plik trzyma to, co potrzebne **zanim** zajrzysz w kod. Reszta leży obok — otwieraj, gdy zmiana
dotyka danego obszaru:

| Plik | Kiedy otworzyć |
|------|----------------|
| [`API.md`](API.md) | dokładasz lub zmieniasz endpoint, upload pliku, maila, OG/SEO |
| [`MIGRATIONS.md`](MIGRATIONS.md) | piszesz migrację albo szukasz, skąd wzięła się kolumna |
| [`DECISIONS-SECURITY.md`](DECISIONS-SECURITY.md) | auth, JWT, OAuth, rate limiting, obsługa 401/429 na froncie |
| [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md) | kalendarz 1:1, cele, filmy, zdjęcia, zgody RODO art. 9, prywatne notatki |
| [`INFRA.md`](INFRA.md) | deploy, Docker, JVM, kopie zapasowe, CI/CD, zmienne środowiskowe |
| `docs/manual.md` | manual aplikacji (lokalny, `docs/` jest w `.gitignore`) |

---

## Niezmienniki — przeczytaj, zanim ruszysz kod

Reguły, które da się złamać **bez błędu i bez ostrzeżenia**. Każda ma za sobą test albo incydent.
Jeśli zmiana dotyka któregoś punktu — otwórz wskazany plik **przed** pisaniem kodu.

**Upload, pliki, obrazy → [`API.md`](API.md)**
- Nowy folder pod `uploads/` jest **domyślnie prywatny** — publiczny dopiero po wpisie na białą listę `PUBLIC_FOLDERS` w `FileController`. `trainingphotos/` (dane zdrowotne) celowo tam nie wchodzi.
- Nowy format obrazu to **trzy miejsca**: `StorePolicy`, sygnatura w `ImageFormat.sniff`, wzorzec w `StoragePaths.FILENAME`. Pominięcie trzeciego → plik da się zapisać, ale nie odczytać ani skasować.
- Sufit 24 mln pikseli i `CONCURRENT_DECODES = 1` są **policzone** pod `mem_limit: 384m`. Zmieniasz jedno — przelicz drugie.

**Rate limiting i sesje → [`DECISIONS-SECURITY.md`](DECISIONS-SECURITY.md)**
- Każdy endpoint z `MultipartFile` musi trafić do kubełka `upload` w `RateLimitFilter.RULES` — `RateLimitCoverageTest` wywala build, gdy któryś ucieknie.
- Nowy kontroler = własna reguła w `RULES` albo świadomy wpis w allowliście `INTENTIONALLY_GENERIC`.
- **Sesję kończy wyłącznie 401/403 od serwera.** 429, timeout ani błąd sieci — nigdy. Nierozpoznany błąd znaczy „nie wiem", a nie „wyloguj".
- Hasło sprawdzane **przed** zgłoszeniem blokady konta (anty-enumeracja). Nie odwracać kolejności.
- Google OAuth jest **wyłączone** flagą `GOOGLE_LOGIN_ENABLED`. Kolejność włączania: klucze → profil `oauth2` → flaga. Dopóki flaga stoi na `false`, markery „w przygotowaniu" w polityce prywatności **muszą** zostać.

**Maile → [`API.md`](API.md)**
- Wysyłka do wielu odbiorców to **jedno zadanie** na `mailCampaignExecutor`, nigdy jedno zadanie na osobę.
- Szkielet HTML maila istnieje **tylko** w `BrandedMailSender` — nie dopisuj lokalnego szablonu „bo ten jeden mail jest inny".
- Nagłówki `List-Unsubscribe` lecą **wyłącznie** na wysyłce masowej, nigdy na transakcyjnej.

**SEO i nginx → [`API.md`](API.md)**
- Gdy wrócą podstrony z podglądem dla botów (przepisanie na `/og/` po user-agencie w `nginx.conf`), lista botów **nigdy** nie zawiera wyszukiwarek — pętla przekierowań = „Redirect error" w Search Console. Dziś podgląd ma tylko strona główna; `seo-smoke.sh` i tak sprawdza, że Googlebot nie dostaje meta-refresh.
- Dokładasz zewnętrzny zasób → dopisz host do CSP w `nginx.conf` **i** do sekcji 4 w `seo-smoke.sh`. Dev server Vite nie wysyła CSP, więc lokalnie nic nie pęknie.

**Kalendarz 1:1 → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- Zadanie i trening to **dwa wiersze** (`kind`), nigdy jedno pole na wspólnym wierszu.
- Wpis trenera jest dla podopiecznego **tylko do odczytu**; klucz to `created_by_admin`, nigdy `last_modified_by_admin`.
- Wklejenie trafia do `targetAthleteId` — kalendarza otwartego na ekranie, nie do właściciela źródła.
- `attachments`: `null` = nie ruszaj · `[]` = wyczyść · lista = zamień. Zapis podopiecznego to pole **ignoruje**, nie odrzuca.
- Liczniki nieprzeczytanych mają **kilka źródeł** (treningi, komentarze, usunięcia, cele — lista w `TrainingUnreadService`) i dwie krawędzie (`updated_at` + `seen_through`); predykat żyje w sześciu miejscach i zmienia się we wszystkich naraz.
- Klasa ze `@Scheduled` **nie może** deklarować `@Transactional` na innej metodzie — samowywołanie omija proxy i adnotacja milczy. Pilnuje `SchedulerTransactionArchTest`.
- Poszerzenie zakresu danych zdrowotnych = **nowa zgoda** (wyzerowanie `training_consent_at`), nie dopisek do listy.

**Prywatne notatki właściciela → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- Notatka **nigdy** nie jedzie w istniejącym DTO — także jako sam `hasNote`. Typ jest nieosiągalny poza `domain/adminnote` i `api/admin/note`; pilnują tego dwa testy z przeciwnych stron.
- Kasowanie notatki **omija** bramkę `is_athlete` (odczyt i zapis nie omijają). Zdjęcie flagi nie może uwięzić cudzych danych bez ścieżki usunięcia.
- Kopia treningu (`duplicate`, `paste`-COPY) **nie zabiera** notatki — inaczej niż załączniki.
- Notatka jest przypięta wyłącznie do treningu (V1: `training_id NOT NULL`). Notatki „o zajęciach grupowych”, o slotach i terminach z Fire zostały wycięte razem z tymi funkcjami.
- Eksport RODO (`GET /api/admin/users/{id}/export`) **zabiera** notatki o osobie, wszystkich autorów — wyłącznie przez `exportForAthlete`. Nowa tabela z FK do `users` = wpis do eksportu albo do wykluczeń (`UserDataExportCoverageTest`).

**Front → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- `fetchApi` ponawia **tylko** `GET`/`HEAD`. Ponowiony zapis tworzy drugi wpis.
- `staleTime: 0` jest błędem (lawina żądań przy powrocie na kartę) — jest `SHORT_STALE_MS` z `utils/queryFreshness.ts`.
- Surowy `<input type="date">` poza `components/ui/DateInput` wywala build.

**Obrazy i CI → [`INFRA.md`](INFRA.md)**
- Obraz **pakuje** artefakt, który przeszedł testy (`app.jar` / `dist/` jadą z joba testowego artefaktem workflow). **`Dockerfile`, który kompiluje aplikację, to regres** — testowana i wdrażana byłaby inna kompilacja. Ręczny `docker build` wymaga wcześniej `./gradlew clean bootJar && cp build/libs/*.jar app.jar` (front: `npm run build`).
- `COPY app.jar` stoi **nad** blokiem `addgroup … && chown -R app:app /app` — to ten `chown` czyni jara własnością usera kontenera. Przestawienie zostawia go rootowego i nic nie krzyczy.
- Podmiana `@version@` w `application.yml` wymaga `inputs.property('appVersion', …)` w `processResources`. Bez tego po samym bumpie `VERSION` zadanie zostaje UP-TO-DATE: jar dostaje nową nazwę, ale w środku niesie **poprzedni** numer (odtworzone: `…-0.5.23.jar` z `version: '0.5.22'`), a `app.version` karmi Swagger i log startowy.
- Wersja Node'a dla frontu żyje **tylko** w `kd-training-frontend/.nvmrc`. Obraz nie zawiera już Node'a — nie dokładać drugiego miejsca.

**Proces**
- **Migracje SQL są niezmienne** — nawet poprawka komentarza w zaaplikowanym pliku zmienia checksum Flyway i wywala deploy. Wyjątek jeden i tylko teraz: `V1` nie była jeszcze nigdzie wdrożona, więc **do pierwszego deployu na produkcję** wolno ją poprawiać (każda poprawka = reset lokalnej bazy). Od pierwszego deployu — nowa migracja.
- Nowy pakiet w backendzie = własny `package-info.java` z `@NullMarked`. Adnotacja **nie schodzi do podpakietów** (mówi to javadoc JSpecify), więc bez tego pliku każde `@Nullable` w pakiecie niesie pół umowy: sąsiedzi mają nullowość *nieokreśloną*, a nie „niepustą". Pilnuje `NullMarkedPackagesTest` — druga asercja łapie literówkę w nazwie pakietu, którą `compileJava` przepuszcza bez słowa. Uwaga na skutek uboczny: repozytorium w oznaczonym pakiecie dostaje `MethodInvocationValidator`, więc `null` w argumencie rzuca `IllegalArgumentException` zamiast zwrócić pustkę.
- Produkt po polsku, kod (komentarze, logi, commity, PR) po angielsku.
- Commit funkcjonalny zawsze z bumpem `VERSION` — bez tego tag obrazu w GHCR jest nadpisywany. `feat:` → minor, reszta → patch.
- Kopie zapasowe: `rclone copy`, **nigdy** `sync` → [`INFRA.md`](INFRA.md).
- **Akcje GitHub przypięte do SHA** (`uses: owner/action@<sha> # vX.Y.Z`; Dependabot podbija oba) — tag można przestawić na cudzy kod, a `appleboy/ssh-action` dostaje klucz do serwera. Token domyślnie tylko do odczytu (`packages`/`security-events` wyłącznie w jobach, które ich używają); wersja z formularza Deploy idzie przez `env:` i musi wyglądać jak tag obrazu. Kontenery w `docker-compose.prod.yml` mają `no-new-privileges`. Nowa akcja — od razu z SHA.

---

## Stack

Dokładne wersje: `kd-training-backend/build.gradle` i `kd-training-frontend/package.json` — nie
powielać ich tutaj, Dependabot rusza je co tydzień.

- **Backend:** Java 25 · Spring Boot 4.1 · Spring Security 7 + JWT (jjwt) · Spring Data JPA ·
  **PostgreSQL 18** · Flyway · Spring Mail · Actuator · JSpecify (`@NullMarked`) · springdoc-openapi ·
  Testcontainers + JUnit 5 · Caffeine (wyłącznie programowo: limity, cache filtra JWT — bez Spring Cache).
- **Frontend:** React 19 · TypeScript 6 · Vite 8 · Tailwind 4 · TanStack Query 5 · React Router 7 ·
  i18next (tylko PL) · date-fns · lucide-react · Vitest 5 + Playwright (E2E). Node z `.nvmrc` (26).
- **Infrastruktura:** Docker Compose + nginx (w obrazie frontu), obrazy z GHCR, kopie `rclone` (crypt)
  na Google Drive, monitor kopii healthchecks.io. Serwer — do ustalenia (patrz „Fork z Fire Academy”).

### Struktura repozytorium
```
kd-training-backend/
kd-training-frontend/     # nginx.conf + scripts/prerender.mjs
kd-training-hub/          # Docker Compose (dev/prod), .env, setup-swap.sh, seo-smoke.sh, backup, RESTORE.md
docs/                     # ⚠️ w .gitignore — notatki lokalne
.github/workflows/        # CI/CD
CLAUDE.md · API.md · MIGRATIONS.md · DECISIONS-SECURITY.md · DECISIONS-TRAINING.md · INFRA.md · VERSION
```

---

## Baza danych — Flyway

**Obecny stan: V1 (schemat startowy, jeszcze nigdzie niewdrożony). Kolejna migracja: V2.**

`V1__initial_schema.sql` to sklejka V1–V42 z Fire Academy bez tabel wyciętych funkcji — jak powstała
i jak sprawdzono, że jest identyczna → [`MIGRATIONS.md`](MIGRATIONS.md).

---

## API — mapa prefiksów

Pełna lista endpointów wraz z pułapkami → [`API.md`](API.md).

| Prefiks | Dostęp | Co tam jest |
|---------|--------|-------------|
| `/api/auth` | brak | rejestracja, logowanie, weryfikacja maila, reset hasła, refresh |
| `/api/user` | zalogowany | profil, zgody (w tym art. 9), avatar, wylogowanie wszędzie, usunięcie konta |
| `/api/user/my-training` | podopieczny (`is_athlete` + zgoda art. 9) | kalendarz 1:1, cele, statystyki, komentarze, zdjęcia |
| `/api/admin` | ROLE_ADMIN | użytkownicy (lista, profil, role, eksport RODO, usunięcie), podopieczni, plan 1:1, cele, biblioteka filmów, szablony, notatki |
| `/api/public` | brak | wypis z marketingu |
| `/api/files` | brak | streaming plików z białej listy folderów (dziś tylko `avatars`) |
| `/og/` · `/sitemap.xml` | brak | Open Graph strony głównej, mapa strony (`/`, `/polityka-prywatnosci`) |
| `/api/dev` | profil `dev` | logowanie na skróty, lista userów |

---

## Frontend Routes

| Ścieżka | Komponent | Opis |
|---------|-----------|------|
| `/` | HomePage | Wizytówka — **zaślepka** do czasu treści i projektu od klienta |
| `/logowanie` · `/rejestracja` | LoginPage · RegisterPage | `/login`, `/register`, `/admin/login`, `/admin/register` → redirect tutaj; po zalogowaniu powrót na zapamiętaną ścieżkę |
| `/uzupelnij-profil` | ProfileCompletionPage | domknięcie konta po Google (brakujące pola + polityka prywatności) |
| `/moje-konto` | MyAccountPage | profil + kafelek planu treningowego (tylko `isAthlete`) |
| `/moje-konto/plan-treningowy` | MyTrainingCalendarPage | strefa podopiecznego: zgoda art. 9 → cele, kalendarz, wiadomości, statystyki |
| `/settings` | SettingsPage | avatar, dane, hasło, zgoda marketingowa, usunięcie konta |
| `/polityka-prywatnosci` | PrivacyPolicyPage | polityka — **wersja robocza** (dane administratora do uzupełnienia) |
| `/wypisz-sie` | MarketingUnsubscribePage | rezygnacja z marketingu z linku w mailu |
| `/admin/podopieczni` · `/admin/uzytkownicy` | AdminPage | panel trenera: podopieczni (kalendarz 1:1, cele, statystyki, biblioteka filmów, szablony), użytkownicy (profil, włączenie planu 1:1, eksport RODO, usunięcie planu/konta, mailing) |
| `/verify-email` · `/reset-password` · `/forgot-password` · `/resend-verification` · `/oauth-callback` | — | przepływy konta |

Nawigacja: Strona główna · (Moje konto — zalogowany) · (Panel admina — admin) · (Zaloguj się — gość).
Marka w nawigacji, stopce i mailach jest **tekstem** („KD Training”) — logo jeszcze nie ma.
Ikony i obrazek OG (`public/icons`, `favicon.*`, `og-default.png`) to wygenerowane zaślepki „KD”.

---

## Język

**Rozróżnienie: język produktu vs język kodu.**

- **Produkt (UI, treści dla użytkownika) — tylko polski.** Backend: `messages.properties` (pl), frontend: `locales/pl/`. Default `preferredLanguage` w bazie i kodzie: `"pl"`. Stringi widoczne dla usera, maile, komunikaty błędów, OG/SEO — po polsku.
- **Kod (komentarze, logi, commity) — angielski.** Komentarze (`//`, `/* */`, Javadoc/JSDoc) i stringi logów (`log.info/warn/error`, `console.*`) piszemy po angielsku (konwencja przyjęta 2026-06-22). Commity/PR też po angielsku.
  - **NIE tłumaczymy na angielski:** `messages.properties`, `locales/*.json`, stringi UI, komunikaty wyjątków pokazywane userowi, treści OG/SEO, dane seedowane (`DevDataSeeder`), wartości asercji w testach sprawdzające polski tekst.
  - **Migracje `db/migration/*.sql` są niezmienne** (zaaplikowane na prodzie → zmiana komentarza = inny checksum Flyway = błąd deployu). Komentarze w istniejących migracjach zostają jak były; nowe piszemy po angielsku.

---

## Local Dev Workflow

**Porty dev:** PostgreSQL `5436` · backend `8083` · frontend (Vite) `5175` · MailHog SMTP `1027`, web UI `8027`

```bash
# 1. Baza danych + MailHog
cd kd-training-hub && docker compose -f docker-compose.dev.yml up -d

# 2. Backend (IntelliJ: Run KdTrainingApplication z profilem dev)
#    lub z terminala:
cd kd-training-backend && ./gradlew bootRun

# 3. Frontend
cd kd-training-frontend && npm run dev
```

Backend wymaga działającego PostgreSQL (port 5436). MailHog (web UI: localhost:8027) przechwytuje emaile wysyłane przez auth flow (weryfikacja konta, reset hasła).

Profil `dev` przy każdym starcie **czyści bazę i odtwarza ją z migracji** (`FlywayConfig`), a
`DevDataSeeder` zakłada konta z hasłem `dev`: admini z `ADMIN_EMAIL` oraz `jan@dev.pl`, `anna@dev.pl`
(podopieczni), `piotr@`, `kasia@`, `marek@dev.pl`. Wyłączenie resetu: `app.dev.reset-on-start=false`.

Porty wybrane tak, żeby nie kolidowały z innymi projektami w `Projects/` (Fire: 5433/8081/5174,
abcwspinania: 5435/3000). Ten sam port Vite co gdzie indziej = Playwright testowałby cudzą aplikację
(`e2e/app-identity.setup.ts` to wyłapuje).

---

## Testy

```bash
./gradlew test                                    # Wszystkie testy
./gradlew test --tests "JwtServiceTest"           # Konkretna klasa
```

**Naming:** `shouldDoSomethingWhenCondition()`, struktura Given/When/Then

**Bramka frontu — `frontend/src/__architecture__/dateInput.test.ts`.** Surowy `<input type="date">`
poza `components/ui/DateInput` wywala build. Safari na macOS **nie zamyka** swojego popovera po
kliknięciu dnia: wartość jest już zapisana, więc klik czyta się jako „nic się nie stało", a data
„pojawia się" dopiero po kliknięciu obok. `DateInput` robi `blur` i **zaraz oddaje fokus** temu
samemu polu — bez tego powrotu reszta przeglądarek (które popover zamykają same) płaciłaby za fix
fokusem na `body`: Enter przestaje zapisywać, a Tab startuje od góry strony. Powrót fokusu popovera
**nie** otwiera, bo natywny picker otwiera się na klik, nie na fokus. Jedno i drugie **nie** dzieje
się przy pisaniu z klawiatury (zdarzenie leci po każdym znaku, więc fokus uciekałby po dniu, przed
miesiącem) ani na dotyku (kółko na iOS wysyła zdarzenie przy każdym przekręceniu i zamyka się
własnym „Done"). Bramka czyta źródła przez `import.meta.glob`, nie `node:fs`, bo ten pakiet nie ma
`@types/node`; osobny test pilnuje, że glob **cokolwiek** widzi — pusty glob przechodziłby zawsze.

---

## Fork z Fire Academy

Ustalone 26.10.2026 (Mateusz): nowy projekt na bazie Fire Academy, żeby **nie robić drugi raz**
tego, co tam już przeszło audyty — automatyzacje, Dependabot, baza, kopie zapasowe, poprawki
bezpieczeństwa. Fire jest źródłem, nie zależnością: historie gita są rozłączne.

### Co przeszło bez zmian (poza nazwami)
- CI (`ci-backend.yml`, `ci-frontend.yml`: testy, obraz z artefaktu, Trivy, SHA-pinned akcje,
  tokeny tylko do odczytu), `deploy.yml`, `dependency-submission.yml`, `dependabot.yml`.
- Konta i bezpieczeństwo: JWT + refresh, blokada po nieudanych logowaniach, `RateLimitFilter`
  (+ test pokrycia), HaveIBeenPwned, polityka haseł, OAuth Google za flagą, CSP w nginx.
- Kalendarz 1:1, komentarze, zdjęcia (30 dni, prywatne), biblioteka filmów YouTube, szablony, cele,
  statystyki z RPE, notatki trenera, zgoda art. 9, eksport RODO (ZIP), usuwanie planu / konta.
- Hub: compose prod z `no-new-privileges`, limity pamięci i logów, swap, backup (`rclone copy`,
  crypt, marker końca zrzutu, healthchecks), `RESTORE.md`, `seo-smoke.sh`.

### Co wycięto (i dlaczego nie ma tego w V1)
Wydarzenia/obozy/szkolenia, typy wydarzeń, kadra, zapisy na wydarzenia, cykliczne treningi
grupowe z płatnościami, zwrotami, dniami wolnymi i odwołanymi zajęciami, nakładka zajęć grupowych
na kalendarz 1:1, waga z trendem i cele wagowe, Spring Cache (cache'ował tylko katalog), publiczne
API katalogu, maile zapisów, strona główna z intro i zdjęciami Fire, logo i grafiki Fire.
Decyzja Mateusza: wyciąć od razu, a stały grafik Kacpra zaprojektować od nowa po odpowiedziach klienta.

### Czym się różni od Fire
- Nazwy: pakiet `pl.kdtraining`, katalogi `kd-training-*`, kontenery `kd-*-prod`, wolumeny `kd_*`,
  pliki backupu `kd-training-backup*`, remote kopii `kd-crypt:` (nie `gdrive-crypt:` — inny folder
  i inne hasła szyfrowania niż Fire, nawet gdyby oba projekty stały na jednym serwerze).
- Porty dev (wyżej), paleta czarno-biała (`index.css`, skala `primary-*`), szablon maili czarno-biały.
- `V1` zamiast 42 migracji; notatki trenera tylko przy treningu; cele bez rodzaju (bez celów wagowych).
- Smoke test w Deploy czyta domenę ze zmiennej `SITE_URL`, nie z literału.

### Przed pierwszym wdrożeniem (otwarte)
1. Domena → podmienić `kdtraining.example` (nginx `server_name` i przekierowania, `robots.txt`,
   `prerender.mjs`, `.env.example`, `AppConfig`, `application*.yml`, JWT `issuer`).
2. Dane administratora → polityka prywatności (sekcja 1), `AdminUserDataExportService.readme()`,
   zgody marketingowe (`auth.json`, `settings.json`); adres kontaktowy → `config/site.ts`,
   `calendar.json` (`withdrawNote`).
3. Treść i projekt wizytówki, logo, ikony, `og-default.png` (dziś zaślepki).
4. Regulamin + akceptacja przy rejestracji (brief klienta, pkt 6) — jeszcze nie zaimplementowane.
5. Serwer: nowa maszyna albo istniejąca. Na istniejącej: osobny katalog `/opt/kd-training`, a skoro
   kontener frontu wystawia porty 80/443, dwa projekty na jednej maszynie wymagają wspólnego reverse
   proxy przed nimi. Rozmiar maszyny decyduje o `mem_limit` (dziś 384m backend jak w Fire — patrz
   INFRA.md, pomiar soak).
6. GitHub: repo, sekrety (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH`,
   `JWT_SECRET` dla CI), zmienna `SITE_URL`, Dependabot alerts + security updates, ochrona `main`.
7. Kopie: `rclone config` z remote'em `kd-crypt` (crypt na własnym folderze Drive),
   `/etc/kd-training-backup.env` z `HEALTHCHECK_URL`, wpis w crontab (instrukcja na końcu skryptu).
8. SMTP: konto pocztowe nadawcy (`MAIL_*` w `.env`), `ADMIN_EMAIL` = adres Kacpra.

### Poprawki z Fire w przyszłości
Historia jest rozłączna, więc nic nie przyjdzie samo. Po audycie albo incydencie w Fire sprawdzić
`git -C ../Przemo/fire-academy log` od commita `17142a0` i przenieść ręcznie to, co dotyczy
wspólnych części (lista „Co przeszło” wyżej). Odwrotnie tak samo — poprawka tutaj w części wspólnej
to kandydat do Fire.
