# KD Training — infrastruktura

> Wydzielone z `CLAUDE.md`. Serwer produkcyjny, JVM, kopie zapasowe, CI/CD, zmienne środowiskowe.
> Porty dev zostały w `CLAUDE.md` (sekcja „Local Dev Workflow").

## Infrastruktura

### Serwer produkcyjny
> Liczby i pomiary w tej sekcji (limity pamięci, soak, czasy startu) pochodzą z produkcji **Fire
> Academy** — małej maszyny, na której ten kod dotąd działał. Serwer KD Training nie jest jeszcze
> wybrany; po wyborze przeliczyć `mem_limit` i heap.

- **Swap 2 GB — krytyczny przy ograniczonej pamięci (bez tego OOM).** Przy pierwszym deploy uruchomić raz: `sudo bash setup-swap.sh` (skrypt w `kd-training-hub/`, idempotentny: 2 GB `/swapfile`, swappiness 10, utrwalone w fstab + sysctl)
- ⚠️ **Zapora originu — do zrobienia przed pierwszym wdrożeniem, w repo jej nie ma.** Strona ma stać za Cloudflare (certyfikat Origin w `certs/`), ale bez zapory serwer przyjmie 80/443 od każdego, kto zna jego adres — z pominięciem reguł Cloudflare. Gorzej: nginx bierze adres odwiedzającego z nagłówka `CF-Connecting-IP` (`map` w `kd-training-frontend/nginx.conf`), więc żądanie wprost na serwer podaje dowolny adres przy każdej próbie i obchodzi limit logowania. Dwie rzeczy:
  1. Porty wystawione **tylko na IPv4** — w `docker-compose.prod.yml` `"0.0.0.0:80:80"` i `"0.0.0.0:443:443"`. Przy samym `"80:80"` Docker otwiera je też na IPv6, poza zaporą.
  2. Reguły w `DOCKER-USER` z dopasowaniem `--ctstate DNAT` (Docker publikuje porty przez NAT, więc `INPUT`/ufw tego ruchu nie widzi; sam port bez `DNAT` łapie też wyjścia aplikacji na 443). Wzór: abcwspinania `deploy/origin-firewall.sh`, `setup-origin-firewall.sh`, `origin-firewall.service` i krok w `deploy.yml`. Tam lista zakresów Cloudflare jest wycinana z `set_real_ip_from` w `nginx.conf` — tu takiej listy nie ma, więc trzeba ją dodać (<https://www.cloudflare.com/ips>).

  Sprawdzenie po wdrożeniu, z innej maszyny: `curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://<IP serwera>/` daje `000`, a strona przez domenę działa.
> ⚠️ **Obraz backendu ma domyślny profil `prod`, nie `docker`.** Wcześniej `Dockerfile` ustawiał `SPRING_PROFILES_ACTIVE=docker` — profil, którego **żaden `application-docker.yml` nigdy nie definiował**. Na produkcji ratował to compose (`${SPRING_PROFILES_ACTIVE:-prod}`), ale każde uruchomienie obrazu bez compose (debug na serwerze, nowe środowisko) startowało po cichu **bez ustawień produkcyjnych**: pula 8 zamiast 5, brak `max-connections` broniącego pamięci, gadatliwe logi i CORS na `http://localhost:*`. Nic nie ostrzegało. Domyślna wartość ma być tą samą odpowiedzią co compose, nie pułapką.

- **JVM tuning backendu (mem_limit 384m).** ENTRYPOINT w `kd-training-backend/Dockerfile`: `-XX:MaxRAMPercentage=55.0` (~211 MB heap; + non-heap ~120 MB mieści się w 384 MB z zapasem — przy 75% było ~288 MB heap → ~408 MB > limit = ryzyko OOM-kill i wypychania bezczynnego heapu do swapu), `-XX:MaxMetaspaceSize=128m`, `-XX:+ExitOnOutOfMemoryError`, `-XX:TieredStopAtLevel=1` (C1-only JIT — skraca start na 2 vCPU; wdrożone 2026-06-20, start 103→64 s). Kontener chodzi jako **non-root** (`USER app`). **Bez wymuszonego `-XX:+UseG1GC`** — poniżej 2 GB RAM JVM ergonomicznie wybiera lekszy SerialGC (mniej pamięci natywnej niż G1 na ciasnym boxie). `$JAVA_OPTS` zachowany jako passthrough; w `docker-compose.prod.yml` `JAVA_OPTS=""` (po upgradzie RAM można tam wstawić `-XX:+UseG1GC`).
- **Soak zapisów 2026-10-25 (Fire): wycieku nie ma, ale proces pod obciążeniem nie mieści się w 384 MB.** 3 h na klonie produkcji Fire, ~4 operacje/s po ścieżkach zapisu (trening: dodanie/edycja/usunięcie, zdjęcie w wątku, ukończenie z RPE, waga, zapis na wydarzenie i anulowanie, plan trenera, odczyty — część z tych ścieżek tu wycięto), 119 071 żądań, p95 48 ms, zero ERROR. Sterta przypięta blisko produkcji (`-Xmx200m` wobec ~211 MB z 55% × 384m, SerialGC): podłoga po pełnym GC **51,6–58,2 MB bez trendu**, metaspace 110,5→110,7 MB, klasy płasko. Cały proces stał na **~440 MB RSS (anon)** przez ostatnie 1,5 h, czyli stabilnie — tylko że ponad `mem_limit`. Pozycja „non-heap ~120 MB” wyżej jest zaniżona: sam metaspace to ~111 MB, a do tego code cache, stosy wątków i bufory natywne dekodowania zdjęć. Test szedł z limitem 2 GB, więc to pomiar bez presji. Na produkcji nadmiar idzie do swapu (compose nie ustawia `memswap_limit`, więc Docker pozwala na 2× `mem_limit`), a nie kończy się zabiciem — przy realnym, dużo mniejszym ruchu. **Po migracji na większą maszynę dać backendowi ≥ 512m.**
  > ⚠️ **Pamięci tego obrazu nie mierzy się na Macu z Apple Silicon.** Obraz jest tylko amd64, więc chodzi pod Rosettą, a ta pod obciążeniem dokłada ~150 MB pamięci natywnej co 5 minut (bloki po 128 MB poza stertą) i kontener ginie z OOM — przy 384m po 32 min, przy 768m po 4,5 min — co wygląda jak wyciek aplikacji, a nim nie jest. Pomiar: `docker cp` jara z obrazu i uruchomienie go na `eclipse-temurin:25-jre-alpine` (arm64) z tą samą linią `java …` z `Dockerfile`. Przy podniesionym limicie dopisać `-XX:+UseSerialGC`: przy 2 GB i ≥2 CPU JVM sam wybiera G1, inaczej niż produkcja.
- **Mail health poza liveness probe.** `management.health.mail.enabled=false` w `application.yml` (domyślnie, niezależnie od env). Wolny SMTP (~10 s) przekraczał 5 s timeout docker healthchecka `/actuator/health` → fałszywe „unhealthy" → zbędny restart. Maile nie są liveness-critical. (W compose env `MANAGEMENT_HEALTH_MAIL_ENABLED=false` zostaje jako redundantny, jawny override.)

### Kopie zapasowe

`kd-training-hub/kd-training-backup.sh` — cron roota 03:00, zrzut całej bazy + wolumen uploadów,
wysyłka na `kd-crypt:` (**remote typu `crypt`**, więc rclone szyfruje przed wysłaniem — to jest
to, co obiecuje sekcja 7 polityki prywatności). Lokalnie 7 dni, na Dysku 40 — liczba z Fire, gdzie
jeden Dysk (15 GB) dzieliło kilka projektów i 90 dni by go zapchało (09.10.2026). Remote `kd-crypt`
ma własny folder i własne hasła szyfrowania — nie współdzieli niczego z kopiami Fire.
Archiwum uploadów powstaje **tylko gdy pliki się zmieniły** (odcisk: ścieżka, rozmiar, data
modyfikacji), najrzadziej co 30 dni — każde jest pełne, a odtworzenie bierze najnowsze archiwum
nie późniejsze niż zrzut (szczegóły w RESTORE.md).
**Odtwarzanie: [`kd-training-hub/RESTORE.md`](kd-training-hub/RESTORE.md).** Skrypt jedzie na
serwer przez `deploy.yml`, tą samą drogą co `nginx.conf` — wcześniej istniał **wyłącznie** na
maszynie produkcyjnej, czyli był jedynym plikiem w systemie kopii bez własnej kopii.

> ⚠️ **`rclone copy`, NIGDY `sync`.** `sync` doprowadza cel do identyczności ze źródłem, **łącznie
z kasowaniem** — a skrypt przycina lokalnie do 7 dni, więc następnego dnia `sync` usuwał te same
pliki na Dysku. Efekt był podwójny: realne archiwum to było 7 dni (błąd zauważony po tygodniu =
nie ma z czego wracać), a cokolwiek zniszczyłoby `/backups` na serwerze propagowało się do chmury
w ciągu doby — czyli kopia off-site chroniła przed wszystkim oprócz katastrofy, dla której powstała.
Zdalną historię przycina osobna, dużo wolniejsza linijka (`rclone delete --min-age 40d`).

> ⚠️ **Zrzut dostaje właściwą nazwę dopiero po sprawdzeniu markera `PostgreSQL database dump
complete`.** Powłoka tworzy plik, zanim `pg_dump` cokolwiek odda, więc przerwany zrzut zostawia coś,
co wygląda dokładnie jak kopia. **Sam test gzipa tego nie łapie** — obcięty zrzut zwykle jest
poprawnym plikiem gzip (sprawdzone: `gunzip -t` przechodzi), po prostu SQL w środku urywa się w
połowie. Praca idzie do `.part`, marker jest jedynym tanim dowodem, że baza doszła do końca. Archiwum
plików analogicznie: `tar tzf` czyta je z powrotem, zanim dostanie prawdziwą nazwę.

> ⚠️ **Cisza jest awarią, więc pilnujemy ciszy.** `set -e` kończy skrypt bez słowa, a poczta crona
do roota na maszynie w chmurze nie dociera nigdzie — kopie potrafią przestać powstawać w marcu i
zostać zauważone w sierpniu. Skrypt pinguje zewnętrzny monitor po **udanym** przebiegu i na `/fail`
przy błędzie; brak sygnału zapala alarm po ich stronie. To wykrywa też przypadki, których żaden mail
o błędzie nie zgłosi: wyłączonego crona, wyłączoną maszynę, skasowany skrypt. URL to sekret —
`/etc/kd-training-backup.env` na serwerze (chmod 600), **nigdy w repo**; brak pliku = ping po cichu
pomijany, więc skrypt działa i bez niego.

### CI/CD (GitHub Actions)

> ⚠️ **Obrazy PAKUJĄ artefakt, który przeszedł testy — nie budują własnego.** Wcześniej oba
> `Dockerfile`'e kompilowały aplikację od nowa (`RUN ./gradlew bootJar -x test`, `COPY . .` +
> `npm run build`), mimo że sąsiedni job w tym samym przebiegu dopiero co to zrobił. To nie była
> kwestia czasu, tylko tego, że **testowana i wdrażana była inna kompilacja**, na innej maszynie
> (front dodatkowo na innym Node: suite na 24, obraz na `FROM node:26-alpine`), a tej drugiej nic
> nigdy nie uruchomiło. Teraz `app.jar` i `dist/` powstają w jobie testowym i jadą do joba `docker`
> jako artefakt workflow (`upload-artifact` / `download-artifact`, `if-no-files-found: error`),
> a `Dockerfile`'e mają wyłącznie warstwę runtime. **Dockerfile, który kompiluje, to regres.**
>
> **Ręczny build obrazu na laptopie wymaga wcześniejszego zbudowania artefaktu** (compose dev nie
> buduje obrazów, więc jedyną zautomatyzowaną ścieżką jest CI):
> ```bash
> cd kd-training-backend && ./gradlew clean bootJar && cp build/libs/*.jar app.jar && docker build -t kd-training-backend .
> cd kd-training-frontend && nvm use && npm ci && npm run build && docker build -t kd-training-frontend .
> ```
> `clean` nie jest ozdobnikiem: `build/libs` trzyma po jarze na każdą kiedykolwiek zbudowaną wersję,
> a `assemble` dokłada `-plain.jar` **bez `Main-Class`**, który sortuje się *przed* właściwym — czyli
> `cp build/libs/*.jar` bez `clean` potrafi wybrać jar budujący poprawny obraz, który umiera na
> starcie. W CI pilnuje tego asercja `-eq 1`.
> Przed zbudowaniem obrazu job `docker` sprawdza jeszcze dwie rzeczy, bo to ostatni moment, w którym
> są tanie: że artefakt w ogóle dojechał w oczekiwanym układzie (`app.jar` / `dist/index.html`, nie
> `dist/dist`) i że **jar deklaruje w środku tę wersję, którą właśnie tagujemy** — ta druga asercja
> łapie od drugiej strony dokładnie tę usterkę, którą naprawia `inputs.property` w `build.gradle`.
> Oba `.dockerignore` są **deny-by-default** (`*` + wyjątek na to, co obraz kopiuje) — lista wykluczeń
> wymaga dopisywania każdego nowego katalogu, lista jednego wpuszczonego nie ma jak się zestarzeć.

> ⚠️ **Wersja frontu: `kd-training-frontend/.nvmrc` jest jedynym źródłem prawdy** (dziś `26`),
> czytany przez `setup-node` (`node-version-file`). Po przeniesieniu builda do CI obraz nie zawiera
> już Node'a, więc nie ma drugiego miejsca, które mogłoby się rozjechać. Zmiana wersji = zmierzyć
> suite, nie założyć: przy vitest 5 / jsdom 30 node 26 przechodzi cały zestaw (w bliźniaczym repo na
> vitest 4 ta sama wersja wywalała 32 testy przez globalny `localStorage`).

- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main + **skan CVE obrazu (Trivy, `ignore-unfixed`)**. Uwaga na dwa progi, bo to nie jest jeden krok: **raport SARIF bierze HIGH i CRITICAL i nigdy nie wywala builda** (`exit-code: 0`, ląduje w zakładce Security), a **bramka bierze wyłącznie CRITICAL i wywala** (`exit-code: 1`). Czyli HIGH jest widoczne, ale nie blokuje — świadomie, żeby build nie czerwienił się od podatności, na które nie ma jeszcze łatki w obrazie bazowym. Tylko na merge'u do `main`, nie blokuje `deploy.yml`; znalezisko = czerwony build proszący o bump obrazu bazowego. Nie wracać do `exit-code: 0` w bramce (skan, który nic nie blokuje, to skan, którego nikt nie czyta) — jeśli szumi, zawężać `severity`. Ten sam kształt raport+bramka powtarza się w cotygodniowych skanach obrazu i obrazu Postgresa
- Nazwa obrazu ma **jedno** źródło: krok `Read version and image owner` wypluwa `owner`
  (`github.repository_owner` zmałolitrowany — GHCR wymaga małych liter, a organizacja nazywa się
  `NextStepProDev`), z którego korzystają i `metadata-action`, i wszystkie `image-ref` Trivy'ego.
  Wcześniej stał tam wpisany na sztywno literał w 8 miejscach — dwie pisownie jednej nazwy, zgodne
  wyłącznie przez przypadek, do pierwszej zmiany nazwy organizacji albo forka
- Oba workflow mają **własny plik na liście `paths`** — bez tego PR ruszający wyłącznie CI nie
  odpalał CI i wjeżdżał na main nieprzetestowany, a dziś przez te pliki przechodzi artefakt wdrożeniowy
- ⚠️ Każdy `upload-sarif` ma `always() && hashFiles('…') != ''`, nie samo `always()`. Gołe `always()`
  obejmuje też przypadek, w którym skan **w ogóle nie ruszył**, bo padł wcześniejszy krok joba —
  wtedy upload kończy się „Path does not exist" i **ten** błąd trafia do adnotacji przebiegu,
  przykrywając prawdziwą przyczynę. Zdarzyło się w bliźniaczym projekcie: build obrazu padł na
  zerwanym połączeniu z `auth.docker.io`, a jedyny widoczny komunikat mówił o brakującym SARIF-ie.
  Strażnik nazywa **plik tego joba** (skan Postgresa ma własny). `upload-artifact` tej wady nie ma —
  przy braku plików domyślnie tylko ostrzega
- `deploy.yml`: ręczny trigger → SSH → `docker compose pull && up -d` → health backendu → smoke test pod `vars.SITE_URL`

### Zmienne środowiskowe (`.env`)
`POSTGRES_DB/USER/PASSWORD`, `MAIL_HOST/PORT/USERNAME/PASSWORD`, `JWT_SECRET`, `GHCR_OWNER`, `VERSION`, `ADMIN_EMAIL`, `CORS_ORIGINS`, `APP_BASE_URL`, `SITE_URL`
GitHub: sekrety `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH` (Deploy) i `JWT_SECRET` (testy w CI); zmienna repozytorium `SITE_URL` (smoke test po wdrożeniu).
Opcjonalne: `ADMIN_HIDDEN_EMAILS` (CSV — konta techniczne/deweloperskie z adminem do testów, **ukryte na liście użytkowników** w panelu, filtr w SQL `AdminEmailConfig.isHiddenEmail`); profil `oauth2`: `OAUTH2_GOOGLE_CLIENT_ID/SECRET`

---
