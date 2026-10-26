# Odtwarzanie z kopii zapasowej

Procedura na dzień, w którym baza produkcyjna przestaje istnieć. **Przećwicz ją, zanim będzie
potrzebna** — kopia, z której nigdy nie odtwarzano, jest hipotezą, nie kopią zapasową. Sekcja
„Ćwiczenie" na końcu opisuje, jak to zrobić bez dotykania produkcji.

Kopie robi `kd-training-backup.sh` (cron roota, 03:00). Dwa osobne zbiory:

| co | gdzie lokalnie | ile dni | na Dysku Google |
|---|---|---|---|
| zrzut bazy (co noc) | `/backups/db/RRRR-MM-DD.sql.gz` | 7 | 40 |
| pliki (avatary, zdjęcia, galerie) — **tylko gdy się zmieniły**, najrzadziej co 30 dni | `/backups/files/RRRR-MM-DD.tar.gz` | 7, najnowsze zawsze | 40 |

**Archiwum plików nie powstaje co noc.** Skrypt porównuje listę plików (nazwa, rozmiar, data
modyfikacji) z poprzednią i pakuje je tylko wtedy, gdy coś się zmieniło — albo gdy ostatnie archiwum
ma 30 dni, żeby przycinanie Dysku po 40 dniach nigdy nie zostawiło go bez archiwum. **Każde archiwum
jest pełne**, nie przyrostowe. Do odtworzenia bierzesz **najnowszy zrzut bazy i najnowsze archiwum
plików z tego samego dnia albo wcześniejsze** — brak nowszego archiwum znaczy dokładnie tyle, że
pliki od tamtej pory się nie zmieniły. Stan porównania: `/var/lib/kd-training-backup/files-state`;
jego skasowanie wymusza archiwum przy najbliższym przebiegu.

40 dni, nie 90 — liczba odziedziczona z Fire Academy, gdzie jeden Dysk (15 GB) dzieliło kilka
projektów (policzone 09.10.2026). Do przeliczenia, gdy znany będzie Dysk tego projektu.

Zdalny dysk to `kd-crypt:` — **remote typu `crypt`**, czyli rclone szyfruje pliki przed
wysłaniem i Google nie widzi ich treści ani prawdziwych nazw. Deszyfrowanie dzieje się samo przy
pobieraniu przez rclone; bez konfiguracji rclone z tej maszyny pliki są bezużyteczne. To także
znaczy, że **utrata konfiguracji rclone = utrata dostępu do kopii** — patrz „Czego pilnować".

---

## 1. Skąd wziąć kopię

Jeśli pliki są jeszcze na serwerze, pomiń ten krok. Jeśli nie:

```bash
rclone ls kd-crypt:db | sort -k2 | tail               # zrzuty bazy: co noc
rclone ls kd-crypt:files | sort -k2 | tail            # pliki: tylko dni ze zmianą
rclone copy kd-crypt:db/2026-08-20.sql.gz /tmp/restore/
rclone copy kd-crypt:files/2026-08-12.tar.gz /tmp/restore/   # najnowsze ≤ data zrzutu
```

Daty zrzutu i archiwum plików **nie muszą być równe** — weź **najnowsze** archiwum nie późniejsze
niż zrzut. Ono zawsze pasuje, bo gdyby pliki zmieniły się przed zrzutem, powstałoby nowsze.
Każde wcześniejsze archiwum może nie mieć plików, które baza już zna.

Sprawdź, czy zrzut jest kompletny, **zanim** cokolwiek skasujesz:

```bash
gunzip -c /tmp/restore/2026-08-20.sql.gz | tail -20 | grep "PostgreSQL database dump complete"
```

Brak tej linijki = plik jest ucięty. Weź starszy i nie ruszaj produkcji.

---

## 2. Odtworzenie bazy

> ⚠️ Kasuje bieżącą zawartość bazy. Upewnij się, że odtwarzasz właściwy dzień.

```bash
cd /opt/kd-training
docker compose -f docker-compose.prod.yml stop backend      # nikt nie pisze w trakcie

docker compose -f docker-compose.prod.yml exec -T postgres \
  sh -c 'dropdb --force -U kdtraining kdtraining && createdb -U kdtraining kdtraining'

gunzip -c /tmp/restore/2026-08-20.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U kdtraining -d kdtraining

docker compose -f docker-compose.prod.yml start backend
```

Backend zatrzymujemy celowo: Flyway i JPA piszą przy starcie, a odtwarzanie do bazy, w której coś
się zmienia, kończy się konfliktami kluczy w połowie.

Bazę zakładamy od nowa, zamiast wgrywać zrzut na istniejącą: zrzut tworzy tabele od zera, więc na
niepustej bazie każde `CREATE TABLE` kończy się błędem, a `COPY` dokłada wiersze do starych —
psql bez `ON_ERROR_STOP` przechodzi przez to bez zatrzymania i zostawia bazę w stanie mieszanym.
Świeża baza to dokładnie warunki „Ćwiczenia” niżej. Poprawione 09.10.2026.

---

## 3. Odtworzenie plików

```bash
docker run --rm \
  -v kd-training_kd_uploads_data_prod:/data \
  -v /tmp/restore:/backup:ro \
  alpine sh -c "rm -rf /data/* && tar xzf /backup/2026-08-20.tar.gz -C /data"
```

**Sprawdź nazwę wolumenu, zanim go użyjesz:** `docker volume ls | grep uploads`.

Prefiks nadaje compose i bierze go z nazwy katalogu, w którym leży plik compose — czyli nazwa wyżej
jest prawdziwa tylko dopóki projekt stoi w katalogu `kd-training`. To nie jest kosmetyka: podanie
nieistniejącej nazwy w `docker run -v` **nie jest błędem** — Docker zakłada wtedy nowy, pusty wolumen.
Przy odtwarzaniu oznacza to rozpakowanie kopii w próżnię, a przy robieniu kopii — spakowanie niczego.
Dlatego `kd-training-backup.sh` sprawdza istnienie wolumenu (`docker volume inspect`) i przerywa,
zamiast wyprodukować poprawny, pusty i zupełnie bezużyteczny plik.

---

## 4. Sprawdzenie, czy się udało

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U kdtraining -d kdtraining -c \
  "SELECT (SELECT count(*) FROM users) AS users,
          (SELECT count(*) FROM personal_trainings) AS treningi,
          (SELECT count(*) FROM enrollments) AS zapisy;"

curl -sf https://kdtraining.example/actuator/health && echo " backend żyje"
```

Liczby porównaj z tym, czego się spodziewasz. Zero użytkowników po odtworzeniu znaczy, że zrzut był
pusty — wróć do kroku 1 i weź starszy.

---

## Ćwiczenie (zrób to raz, na spokojnie)

Bez dotykania produkcji, na dowolnej maszynie z Dockerem:

```bash
docker run -d --name restore-test -e POSTGRES_PASSWORD=test \
  -e POSTGRES_USER=kdtraining -e POSTGRES_DB=kdtraining -p 55432:5432 postgres:18-alpine
sleep 5
gunzip -c 2026-08-20.sql.gz | docker exec -i restore-test psql -U kdtraining -d kdtraining
docker exec -i restore-test psql -U kdtraining -d kdtraining -c "SELECT count(*) FROM users;"
docker rm -f restore-test
```

Jeśli liczba użytkowników się zgadza — kopie działają i wiecie o tym, zamiast zakładać.

> ⚠️ **Wersja obrazu musi zgadzać się z produkcją** (dziś `18-alpine`). Zrzut z nowszego Postgresa
> wgrany do starszego potrafi paść w połowie albo — gorzej — przejść częściowo, i wtedy ćwiczenie
> daje fałszywe poczucie bezpieczeństwa. Przy każdej zmianie majora bazy popraw tę linijkę razem
> z `docker-compose.prod.yml`.

---

## Czego pilnować

- **Konfiguracja rclone jest równie ważna jak same kopie.** Zaszyfrowane pliki bez niej to szum.
  Trzymaj kopię `~/.config/rclone/rclone.conf` (albo samych haseł remote'u `crypt`) w menedżerze
  haseł, poza tym serwerem. Utrata serwera razem z konfiguracją = utrata wszystkich kopii.
- **Cisza to awaria.** Skrypt pinguje monitor po każdym udanym przebiegu; brak sygnału ma zapalić
  alarm. Jeśli nie skonfigurowano `HEALTHCHECK_URL`, nikt się nie dowie, że kopie przestały powstawać.
- **Sprawdzaj ćwiczeniem, nie logiem.** Log mówi, że plik powstał. Tylko odtworzenie mówi, że da się
  z niego wrócić.
