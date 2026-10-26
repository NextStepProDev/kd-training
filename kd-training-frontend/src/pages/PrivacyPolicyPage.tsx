import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Seo } from '../components/seo/Seo'
import { GOOGLE_LOGIN_ENABLED } from '../config/features'
import { CONTACT_EMAIL } from '../config/site'

const LAST_UPDATED = '26 października 2026'

/**
 * Google sign-in is described here in full while it is still switched off, so that the day it goes
 * live nobody has to be re-informed about a processor that appeared overnight. A privacy notice has
 * to be true when somebody reads it, though, so every place describing it carries this marker until
 * {@link GOOGLE_LOGIN_ENABLED} says otherwise — one flag, and the page stops promising what the
 * application does not yet do.
 */
const notYet = (title: string) => GOOGLE_LOGIN_ENABLED ? title : `${title} — w przygotowaniu`

export function PrivacyPolicyPage() {
  const { hash } = useLocation()

  // The consent screen links at #plan-treningowy: someone about to tick a consent box has to land
  // on the paragraph describing what they are consenting to, not at the top of a long page.
  // Deferred two frames because ScrollToTop resets the window on every navigation.
  useEffect(() => {
    if (!hash) return
    const id = hash.slice(1)
    let inner = 0
    const outer = requestAnimationFrame(() => {
      inner = requestAnimationFrame(() => {
        document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    })
    return () => {
      cancelAnimationFrame(outer)
      cancelAnimationFrame(inner)
    }
  }, [hash])

  return (
    <div className="min-h-screen bg-surface-950">
      <Seo
        title="Polityka prywatności"
        description="Polityka prywatności KD Training — jakie dane zbieramy, w jakim celu, jak długo je przechowujemy i jakie prawa Ci przysługują."
        path="/polityka-prywatnosci"
      />
      <div className="relative overflow-hidden bg-gradient-to-b from-surface-900 to-surface-950 border-b border-surface-800">
        <div className="absolute inset-0 opacity-5">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute bottom-0 right-1/4 w-64 h-64 bg-primary-700 rounded-full blur-3xl" />
        </div>
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <h1 className="text-3xl sm:text-4xl font-bold text-surface-100 mb-3">
            Polityka prywatności
          </h1>
          <p className="text-surface-400 text-lg max-w-xl mx-auto">
            Transparentność i bezpieczeństwo Twoich danych to nasz priorytet.
          </p>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16 space-y-6">

        <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
          <p className="text-surface-300 leading-relaxed">
            Dbamy o Twoje dane osobowe i zawsze będziemy dokładać wszelkich starań, aby należycie je chronić.
            Niniejsza polityka prywatności wyjaśnia, jakie dane zbieramy, w jakim celu i na jakiej podstawie prawnej,
            jak długo je przechowujemy oraz jakie prawa Ci przysługują. Napisana jest w sposób prosty i zrozumiały —
            bez zbędnego żargonu prawniczego.
          </p>
          <p className="text-surface-500 text-sm mt-4">
            Ostatnia aktualizacja: {LAST_UPDATED}
          </p>
        </div>

        <Section title="1. Administrator danych osobowych">
          <p className="text-surface-300 leading-relaxed">
            Administratorem Twoich danych osobowych jest:
          </p>
          <div className="mt-4 bg-surface-800/50 rounded-xl p-4 space-y-1 text-surface-300 text-sm">
            {/* Placeholder until the client sends the company details (name, address, NIP). */}
            <p className="font-semibold text-surface-200">[Dane administratora — do uzupełnienia]</p>
            <p>
              E-mail:{' '}
              <a href={`mailto:${CONTACT_EMAIL}`} className="text-primary-400 hover:text-primary-300 transition-colors">
                {CONTACT_EMAIL}
              </a>
            </p>
          </div>
          <p className="text-surface-400 text-sm mt-4 leading-relaxed">
            W sprawach dotyczących danych osobowych możesz kontaktować się z nami pod powyższym adresem e-mail.
            Staramy się odpowiadać na wszystkie wiadomości w ciągu 72 godzin.
          </p>
        </Section>

        <Section title="2. Jakie dane zbieramy">
          <p className="text-surface-400 leading-relaxed mb-4">
            Zbieramy wyłącznie dane niezbędne do założenia konta i świadczenia usług treningu personalnego. Nie zbieramy nic ponad to.
          </p>

          <SubSection title="Konto użytkownika (rejestracja)">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              Konto daje dostęp do Twojej strefy podopiecznego. Przy rejestracji zbieramy:
            </p>
            <DataList items={[
              'Imię i nazwisko',
              'Adres e-mail',
              'Numer telefonu',
              'Hasło — przechowywane wyłącznie w postaci zaszyfrowanego hashu (bcrypt), nigdy w formie jawnej',
              'Data i godzina akceptacji polityki prywatności — jako potwierdzenie udzielonej zgody',
            ]} />
          </SubSection>

          <SubSection title="Profil użytkownika (opcjonalnie)">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              W ustawieniach konta możesz dobrowolnie uzupełnić:
            </p>
            <DataList items={[
              'Zdjęcie profilowe (avatar) — jeśli zdecydujesz się je dodać; w każdej chwili możesz je usunąć',
              'Zgoda marketingowa (opcjonalna) — wraz z datą i godziną jej udzielenia oraz unikalnym tokenem umożliwiającym rezygnację z newslettera bez logowania',
            ]} />
          </SubSection>

          <SubSection title="Kalendarz treningów indywidualnych 1:1 (tylko dla podopiecznych)">
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              Jeśli trener prowadzi Cię indywidualnie i włączy Ci kalendarz treningowy, w Twoim koncie
              zapisywane są dodatkowo:
            </p>
            <DataList items={[
              'Plan treningowy — nazwy, opisy i terminy treningów ułożonych przez trenera oraz dodanych przez Ciebie',
              'Oznaczenia wykonania treningu wraz z datą',
              'Ocena odczuwalnego wysiłku (skala 1–10) i Twój komentarz do wykonanego treningu',
              'Zadania z dziennym limitem kalorii (np. „utrzymaj się poniżej 2200 kcal") wraz z oznaczeniem ich wykonania',
              'Wiadomości wymieniane z trenerem przy poszczególnych treningach',
              'Cele treningowe ustawione przez trenera oraz data ich osiągnięcia',
              'Materiały treningowe przypisane do treningu (filmy z ćwiczeniami)',
            ]} />
            <p className="text-surface-500 text-sm mt-3 leading-relaxed">
              Wszystkie te dane są widoczne dla trenera prowadzącego — to podstawowy sens tej funkcji
              i informujemy o tym również bezpośrednio na stronie planu treningowego. Nie są widoczne
              dla innych podopiecznych.
            </p>
            <p className="text-surface-500 text-sm mt-3 leading-relaxed">
              Podanie tych danych jest <span className="text-surface-300 font-medium">całkowicie dobrowolne</span>,
              a odmowa nie wpływa na możliwość udziału w treningach. Zanim po raz pierwszy otworzysz plan
              treningowy, prosimy o osobną, wyraźną zgodę na przetwarzanie danych dotyczących zdrowia —
              szczegóły w punkcie 5.
            </p>
          </SubSection>

          <SubSection title={notYet('Logowanie przez konto Google')}>
            {!GOOGLE_LOGIN_ENABLED && (
              <p className="text-surface-400 text-sm mb-2 leading-relaxed">
                Ta metoda logowania nie jest jeszcze uruchomiona. Opisujemy ją z wyprzedzeniem, żeby zakres
                przetwarzanych danych był znany, zanim funkcja ruszy, a nie ogłaszany po fakcie.
              </p>
            )}
            <p className="text-surface-400 text-sm mb-2 leading-relaxed">
              Konto można będzie założyć także przez Google. W takim wypadku Google przekazuje nam wyłącznie:
            </p>
            <DataList items={[
              'Imię i nazwisko zapisane w koncie Google',
              'Adres e-mail przypisany do konta Google',
              'Identyfikator konta Google — techniczny numer, po którym rozpoznajemy, że to wciąż ta sama osoba przy kolejnym logowaniu',
            ]} />
            <p className="text-surface-500 text-sm mt-2 leading-relaxed">
              Hasło do konta Google nigdy do nas nie trafia — wpisywane jest wyłącznie po stronie Google.
              Nie otrzymujemy dostępu do poczty, kontaktów, kalendarza ani plików na Dysku Google. Numeru
              telefonu Google nam nie przekazuje, więc trzeba go uzupełnić samodzielnie — razem
              z akceptacją tej polityki, o którą prosimy przy pierwszym logowaniu.
            </p>
          </SubSection>

          <p className="text-surface-500 text-sm mt-4">
            Nie korzystamy z plików cookies śledzących, Google Analytics, Facebook Pixel ani żadnych innych narzędzi analitycznych.
          </p>
        </Section>

        <Section title="3. Cel i podstawa prawna przetwarzania">
          <div className="space-y-4">
            <LegalBasis
              purpose="Obsługa konta użytkownika"
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy"
            />
            <LegalBasis
              purpose="Zdjęcie profilowe (avatar)"
              basis="Art. 6 ust. 1 lit. a RODO — dobrowolna zgoda, którą możesz w każdej chwili wycofać, usuwając zdjęcie w ustawieniach konta"
            />
            <LegalBasis
              purpose={notYet('Logowanie przez konto Google')}
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy (założenie konta i dostęp do niego). Korzystanie z tej metody jest dobrowolne: konto można założyć również adresem e-mail i hasłem, a zakres danych jest wtedy taki sam."
            />
            <LegalBasis
              purpose="Weryfikacja adresu e-mail i odzyskiwanie hasła"
              basis="Art. 6 ust. 1 lit. b RODO — wykonanie umowy"
            />
            <LegalBasis
              purpose="Wysyłka wiadomości marketingowych (newsletter)"
              basis="Art. 6 ust. 1 lit. a RODO — Twoja dobrowolna i odrębna zgoda; dodatkowo art. 10 ustawy o świadczeniu usług drogą elektroniczną oraz art. 172 Prawa telekomunikacyjnego. Szczegóły w punkcie 4 poniżej."
            />
            <LegalBasis
              purpose="Prowadzenie indywidualnego planu treningowego (kalendarz 1:1, cele, komunikacja z trenerem)"
              basis="Art. 6 ust. 1 lit. b RODO — przetwarzanie niezbędne do wykonania umowy o świadczenie usług treningu personalnego"
            />
            <LegalBasis
              purpose="Dzienne limity kalorii, oceny odczuwalnego wysiłku, komentarze po treningu i dołączane do nich zdjęcia"
              basis="Art. 9 ust. 2 lit. a RODO — Twoja wyraźna, dobrowolna zgoda na przetwarzanie danych dotyczących zdrowia. Wyrażasz ją jednorazowo, zaznaczając osobne oświadczenie przed pierwszym otwarciem planu treningowego, i możesz ją w każdej chwili wycofać (patrz punkt 5). Traktujemy te dane jako dane szczególnej kategorii i chronimy je surowiej niż pozostałe: widzi je wyłącznie Twój trener prowadzący."
            />
            <LegalBasis
              purpose="Bezpieczeństwo systemu (blokada konta po nieudanych logowaniach, rate limiting)"
              basis="Art. 6 ust. 1 lit. f RODO — uzasadniony interes administratora (ochrona przed nieuprawnionym dostępem)"
            />
          </div>
        </Section>

        <Section title="4. Marketing — wiadomości handlowe">
          <p className="text-surface-300 leading-relaxed mb-4">
            Jeżeli udzielisz <span className="text-surface-200 font-medium">odrębnej, dobrowolnej zgody marketingowej</span>,
            będziemy okazjonalnie wysyłać Ci informacje o nowych materiałach i usługach KD Training na adres
            e-mail przypisany do konta. Zgoda obejmuje łącznie:
          </p>
          <DataList items={[
            'przesyłanie informacji handlowych drogą elektroniczną — art. 10 ustawy z dnia 18 lipca 2002 r. o świadczeniu usług drogą elektroniczną (UŚUDE)',
            'używanie podanego adresu e-mail jako telekomunikacyjnego urządzenia końcowego w celach marketingu bezpośredniego — art. 172 ustawy z dnia 16 lipca 2004 r. Prawo telekomunikacyjne',
          ]} />
          <p className="text-surface-400 text-sm mt-4 leading-relaxed">
            <span className="text-surface-200 font-medium">Podstawa prawna:</span> art. 6 ust. 1 lit. a RODO — Twoja
            wyraźna zgoda wyrażona przez zaznaczenie odpowiedniego pola przy rejestracji lub w ustawieniach konta.
          </p>
          <p className="text-surface-400 leading-relaxed mt-4">
            Zgoda marketingowa jest <span className="text-surface-200 font-medium">całkowicie odrębna</span> od umowy
            o prowadzenie konta — możesz mieć konto bez zgody na marketing i odwrotnie. Brak zgody nie wpływa na
            otrzymywanie maili serwisowych (dotyczących konta i planu treningowego), które wysyłamy zawsze na podstawie
            wykonania umowy (art. 6 ust. 1 lit. b RODO).
          </p>
          <p className="text-surface-300 leading-relaxed mt-4">
            <span className="text-surface-200 font-medium">Wycofanie zgody:</span> możesz w każdej chwili wycofać zgodę
            marketingową na dwa sposoby:
          </p>
          <DataList items={[
            'w ustawieniach swojego konta — przełącznik „Wiadomości marketingowe" w sekcji „Wiadomości marketingowe"',
            'klikając link „Zrezygnuj" w stopce każdej otrzymanej wiadomości marketingowej — działa bez logowania, za pośrednictwem unikalnego tokena rezygnacji',
          ]} />
          <p className="text-surface-400 text-sm mt-4 leading-relaxed">
            Wycofanie zgody <span className="text-surface-200 font-medium">nie wpływa na zgodność z prawem przetwarzania</span>,
            którego dokonano na podstawie zgody przed jej wycofaniem. Dane przechowywane na potrzeby marketingu (data
            udzielenia zgody, token rezygnacji) usuwamy razem z kontem.
          </p>
        </Section>

        <Section id="plan-treningowy" title="5. Plan treningowy 1:1 — dane o zdrowiu">
          <p className="text-surface-300 leading-relaxed mb-4">
            Plan treningowy 1:1 to jedyna część serwisu, w której zbieramy dane o Twoim ciele: dzienne
            limity kalorii, ocenę wysiłku, Twój opis samopoczucia po
            treningu oraz zdjęcia, które sam dołączysz do komentarzy (np. zrzut ekranu z zegarka
            sportowego). W tym kontekście traktujemy je jako <span className="text-surface-200 font-medium">dane
            dotyczące zdrowia</span> — kategorię, którą RODO chroni najmocniej. Dlatego nie wystarcza tu sama
            umowa: prosimy o odrębną, wyraźną zgodę, zanim po raz pierwszy otworzysz plan.
          </p>

          <SubSection title="Kto ma dostęp">
            <p className="text-surface-400 text-sm leading-relaxed">
              Do danych Twojego planu mają dostęp wyłącznie Ty i Twój trener prowadzący. Plan jest wspólny —
              trener widzi Twoje wykonania, oceny wysiłku, komentarze i odhaczone zadania kaloryczne, bo bez
              tego nie da się prowadzić treningu. Inni podopieczni nie widzą tych danych w żadnym zakresie.
            </p>
          </SubSection>

          <SubSection title="Zgoda i jej wycofanie">
            <p className="text-surface-400 text-sm leading-relaxed">
              Zgodę wyrażasz świadomym zaznaczeniem oświadczenia przed pierwszym wejściem do planu — nie
              wynika ona z samego korzystania z serwisu ani z wpisania pomiaru. Zapisujemy datę i godzinę jej
              udzielenia jako wymagany przez RODO dowód. Zgodę możesz wycofać w każdej chwili, pisząc na adres
              podany w punkcie 1 — wtedy usuwamy dane kaloryczne, oceny wysiłku i dołączone zdjęcia,
              a plan przestaje być dostępny. Wycofanie nie wpływa na zgodność z prawem przetwarzania sprzed wycofania i nie ma
              wpływu na Twoje konto. Jeśli trener odbierze Ci status podopiecznego,
              zgoda wygasa automatycznie, a ponowne otwarcie planu wymaga jej udzielenia od nowa.
            </p>
          </SubSection>

          <SubSection title="Zdjęcia dołączane do komentarzy">
            <p className="text-surface-400 text-sm leading-relaxed">
              Do wiadomości przy treningu można dołączyć zdjęcie — najczęściej zrzut ekranu z zegarka
              sportowego albo aplikacji treningowej. Traktujemy je jako{' '}
              <span className="text-surface-200 font-medium">dane dotyczące zdrowia</span>, bo widać na
              nich tętno, strefy wysiłku, tempo czy sen. Dlatego takie zdjęcia{' '}
              <span className="text-surface-200 font-medium">nigdy nie trafiają pod publiczny adres</span> —
              żeby je zobaczyć, trzeba być zalogowanym jako Ty albo jako Twój trener prowadzący, a
              przeglądarka nie zapisuje ich w pamięci podręcznej na dysku.
            </p>
            <p className="text-surface-400 text-sm leading-relaxed mt-3">
              Zdjęcia <span className="text-surface-200 font-medium">usuwamy automatycznie po 30 dniach</span> od
              wysłania — sam komentarz zostaje, znika tylko obraz. Do jednego treningu można dołączyć
              najwyżej 3 zdjęcia. Własne zdjęcie możesz usunąć samodzielnie w dowolnym momencie, bez
              czekania na ten termin; usunąć je może też trener. Prosimy, aby nie wysyłać tą drogą
              dokumentacji medycznej ani zdjęć, na których widać dane innych osób — do przekazania
              takich informacji służy kontakt bezpośredni z trenerem.
            </p>
          </SubSection>

          <SubSection title="Podopieczni niepełnoletni">
            <p className="text-surface-400 text-sm leading-relaxed">
              Dane dotyczące zdrowia osoby, która nie ukończyła 16 lat, przetwarzamy wyłącznie za zgodą rodzica
              lub opiekuna prawnego. Jeśli podopieczny jest niepełnoletni, zgodę odbieramy od opiekuna przed
              włączeniem planu treningowego — kontaktowo, poza serwisem. Opiekun może ją wycofać na tych samych
              zasadach, pisząc na adres kontaktowy.
            </p>
          </SubSection>
        </Section>

        <Section title="6. Jak długo przechowujemy dane">
          <div className="space-y-3 text-surface-300 leading-relaxed">
            <p>
              <span className="text-surface-200 font-medium">Dane konta</span> — przechowywane przez cały czas istnienia
              Twojego konta. Nie usuwamy ich po żadnym z góry ustalonym okresie — decydujesz o tym Ty, usuwając konto.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Usunięcie konta</span> — konto możesz usunąć samodzielnie
              w ustawieniach. Usunięcie trwale kasuje Twoje dane profilowe (w tym zdjęcie profilowe) oraz cały plan
              treningowy 1:1 wraz z komentarzami i zdjęciami. Jest to nasz mechanizm realizacji prawa do bycia
              zapomnianym.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Dane z planu treningowego 1:1</span> (plan, wykonania,
              oceny wysiłku, komentarze, cele, zadania kaloryczne) — przechowywane przez czas trwania współpracy
              trenerskiej i usuwane wraz z kontem. Po zakończeniu współpracy lub po wycofaniu zgody dane dotyczące
              zdrowia — limity kaloryczne oraz oceny wysiłku wraz z komentarzami — usuwamy najpóźniej w ciągu 30 dni.
              Samo wyłączenie planu przez trenera nie kasuje danych — ukrywa je, a po ponownym włączeniu wracają.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Zdjęcia dołączone do komentarzy</span> — usuwane
              automatycznie <span className="text-surface-200 font-medium">30 dni</span> po wysłaniu, niezależnie
              od tego, czy współpraca trwa. Treść komentarza zostaje, znika sam obraz. Wcześniej możesz usunąć
              każde swoje zdjęcie samodzielnie.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Kopie zapasowe</span> — przechowywane do 7 dni na
              serwerze i do 40 dni w zaszyfrowanym archiwum na Google Drive, po czym są automatycznie usuwane.
              Oznacza to, że dane usunięte z bazy mogą pozostawać w kopiach najdłużej przez 40 dni. Kopie służą wyłącznie
              odtworzeniu serwisu po awarii i nie są przeszukiwane w żadnym innym celu.
            </p>
            <p>
              <span className="text-surface-200 font-medium">Tokeny bezpieczeństwa</span> (weryfikacja e-mail: 15 min,
              reset hasła: 1h, sesja: 7 dni) — usuwane automatycznie po wygaśnięciu przez wbudowany mechanizm czyszczenia.
            </p>
          </div>
        </Section>

        <Section title="7. Komu udostępniamy dane">
          <p className="text-surface-300 leading-relaxed font-medium text-lg mb-4">
            Nikomu. I nigdy tego nie zrobimy.
          </p>
          <p className="text-surface-400 leading-relaxed mb-4">
            Twoje dane osobowe nie są sprzedawane, wynajmowane ani przekazywane żadnym podmiotom trzecim w celach
            marketingowych, reklamowych ani żadnych innych celach komercyjnych.
          </p>
          <p className="text-surface-400 leading-relaxed mb-4">
            Wyjątkiem, o którym mówimy wprost, jest <span className="text-surface-200 font-medium">trener prowadzący
            Cię indywidualnie</span>: jeśli masz włączony kalendarz treningów 1:1, Twój plan, oznaczenia wykonania,
            oceny wysiłku, komentarze i cele są dla niego widoczne — po to, żeby prowadzić Twój trening. Inni
            podopieczni nie mają do tych danych dostępu.
          </p>
          <p className="text-surface-400 leading-relaxed mb-4">
            Jedynymi podmiotami, z którymi współpracujemy w ramach technicznego przetwarzania danych, są:
          </p>
          <div className="space-y-3">
            <InfoItem
              title="Dostawca usług hostingowych (serwer w UE)"
              description="Serwer aplikacji i baza danych zlokalizowane w Europejskim Obszarze Gospodarczym."
            />
            <InfoItem
              title="Zewnętrzny serwer poczty e-mail (SMTP)"
              description="Wykorzystywany do dostarczenia wszystkich wiadomości e-mail wysyłanych z systemu — zarówno serwisowych (weryfikacja konta, reset hasła, powiadomienia o koncie), jak i marketingowych (po udzieleniu zgody). Dostawca nie przetwarza Twoich danych w żadnym innym celu."
            />
            <InfoItem
              title={notYet('Google LLC (logowanie przez konto Google)')}
              description="Osoby, które wybiorą logowanie przez Google, uwierzytelnia Google — to Google potwierdza nam tożsamość i przekazuje imię, nazwisko oraz adres e-mail. Nie przekazujemy Google żadnych danych o Twoich treningach ani planie 1:1. Kto zakłada konto adresem e-mail i hasłem, nie ma z tym nic wspólnego."
            />
            <InfoItem
              title="Google LLC (kopie zapasowe)"
              description="Zaszyfrowane kopie zapasowe bazy danych i przesłanych plików przechowywane są na prywatnym, niepublicznym dysku Google Drive. Kopia obejmuje całą bazę, a więc również dane planu treningowego 1:1. Google nie ma dostępu do treści kopii — są zaszyfrowane przed wysłaniem."
            />
          </div>
          <div className="mt-4 bg-surface-800/50 rounded-xl p-4">
            <p className="text-surface-200 font-medium text-sm mb-1">Przekazywanie danych poza EOG</p>
            <p className="text-surface-500 text-sm leading-relaxed">
              Serwer i baza danych pozostają w EOG. Wyjątkiem jest współpraca z Google LLC opisana wyżej —
              Google ma siedzibę w USA, więc w tym zakresie dane mogą być przetwarzane poza EOG. Transfer
              odbywa się na podstawie programu EU‑US Data Privacy Framework (Google jest certyfikowany) oraz
              standardowych klauzul umownych zatwierdzonych przez Komisję Europejską (SCC). Kopie zapasowe są
              dodatkowo szyfrowane przed wysłaniem. Kopię stosowanych zabezpieczeń możesz uzyskać,
              kontaktując się z nami.
            </p>
          </div>
        </Section>

        <Section title="8. Twoje prawa">
          <p className="text-surface-400 leading-relaxed mb-4">
            Na podstawie RODO przysługują Ci następujące prawa:
          </p>
          <div className="space-y-3">
            <Right title="Prawo dostępu" description="Możesz w każdej chwili zapytać, jakie Twoje dane przechowujemy." />
            <Right title="Prawo do sprostowania" description="Jeśli Twoje dane są nieprawidłowe lub niekompletne, możesz żądać ich poprawienia." />
            <Right title="Prawo do usunięcia" description="Możesz zażądać trwałego usunięcia swoich danych — a konto usuniesz też samodzielnie w ustawieniach." />
            <Right title="Prawo do ograniczenia przetwarzania" description="Możesz zażądać ograniczenia przetwarzania Twoich danych w określonych przypadkach." />
            <Right title="Prawo do przenoszalności" description="Możesz zażądać przekazania Twoich danych w ustrukturyzowanym, powszechnie używanym formacie." />
            <Right title="Prawo sprzeciwu" description="Możesz wnieść sprzeciw wobec przetwarzania danych opartego na uzasadnionym interesie." />
            <Right title="Prawo do wycofania zgody" description="Jeśli przetwarzanie odbywa się na podstawie zgody (np. zgoda marketingowa, avatar), możesz ją w każdej chwili wycofać. Wycofanie nie wpływa na zgodność z prawem przetwarzania sprzed wycofania." />
            <Right title="Wycofanie zgody na dane planu treningowego" description="Zgodę na przetwarzanie limitów kalorii, ocen wysiłku i zdjęć możesz wycofać w każdej chwili, pisząc na adres kontaktowy — usuwamy wtedy te dane, a plan treningowy przestaje być dostępny. Twoje konto pozostaje nienaruszone." />
          </div>
          <p className="text-surface-400 text-sm mt-6 leading-relaxed">
            Aby skorzystać z któregokolwiek z powyższych praw, napisz do nas na adres{' '}
            <a href={`mailto:${CONTACT_EMAIL}`} className="text-primary-400 hover:text-primary-300 transition-colors">
              {CONTACT_EMAIL}
            </a>.
            Przysługuje Ci również prawo wniesienia skargi do organu nadzorczego — Prezesa Urzędu Ochrony Danych Osobowych
            (PUODO), ul. Stawki 2, 00-193 Warszawa.
          </p>
        </Section>

        <Section title="9. Bezpieczeństwo danych">
          <p className="text-surface-400 leading-relaxed mb-4">
            Stosujemy wielowarstwowe zabezpieczenia techniczne, aby chronić Twoje dane:
          </p>
          <DataList items={[
            'Hasła przechowywane wyłącznie jako hash bcrypt — nawet my nie znamy Twojego hasła',
            'Tokeny bezpieczeństwa hashowane algorytmem SHA-256 przed zapisem w bazie danych',
            'Szyfrowane połączenie HTTPS na całej stronie',
            'Tokeny JWT z krótkim czasem życia (15 minut) — minimalizacja ryzyka przy ewentualnym wycieku',
            'Konta blokowane automatycznie po wielokrotnych nieudanych próbach logowania (5 prób → blokada na 15 minut)',
            'Ograniczenie liczby żądań (rate limiting) — ochrona przed atakami brute-force',
          ]} />
        </Section>

        <Section title="10. Zmiany polityki prywatności">
          <p className="text-surface-400 leading-relaxed">
            W przypadku istotnych zmian w polityce prywatności poinformujemy Cię o tym z wyprzedzeniem —
            przez e-mail lub komunikat na stronie. Data ostatniej aktualizacji jest zawsze widoczna na górze tej strony.
            Zachęcamy do jej okresowego przeglądania.
          </p>
        </Section>

        <Section title="11. Kontakt w sprawach danych osobowych">
          <p className="text-surface-400 leading-relaxed">
            Jeśli masz pytania dotyczące przetwarzania Twoich danych osobowych, chcesz skorzystać z przysługujących
            Ci praw lub masz jakiekolwiek wątpliwości — napisz do nas. Potraktujemy każde zgłoszenie poważnie
            i odpowiemy tak szybko, jak to możliwe.
          </p>
          <div className="mt-4 flex flex-col gap-2">
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="text-primary-400 hover:text-primary-300 transition-colors font-medium"
            >
              {CONTACT_EMAIL}
            </a>
          </div>
        </Section>

        <div className="text-center pt-4 pb-8">
          <Link
            to="/"
            className="text-sm text-surface-500 hover:text-primary-400 transition-colors"
          >
            Wróć na stronę główną
          </Link>
        </div>

      </div>
    </div>
  )
}

function Section({ id, title, children }: { id?: string; title: string; children: React.ReactNode }) {
  return (
    // scroll-mt keeps the heading clear of the fixed navbar when reached through a #hash link
    <div id={id} className="scroll-mt-24 bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
      <h2 className="text-xl font-semibold text-surface-100 mb-5">{title}</h2>
      {children}
    </div>
  )
}

function SubSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-4">
      <h3 className="text-sm font-semibold text-surface-300 uppercase tracking-wider mb-2">{title}</h3>
      {children}
    </div>
  )
}

function DataList({ items }: { items: string[] }) {
  return (
    <ul className="space-y-1.5">
      {items.map((item, i) => (
        <li key={i} className="flex items-start gap-2 text-surface-400 text-sm leading-relaxed">
          <span className="mt-1.5 w-1.5 h-1.5 rounded-full bg-primary-500 shrink-0" />
          {item}
        </li>
      ))}
    </ul>
  )
}

function LegalBasis({ purpose, basis }: { purpose: string; basis: string }) {
  return (
    <div className="bg-surface-800/50 rounded-xl p-4">
      <p className="text-surface-200 font-medium text-sm mb-1">{purpose}</p>
      <p className="text-surface-500 text-sm leading-relaxed">{basis}</p>
    </div>
  )
}

function InfoItem({ title, description }: { title: string; description: string }) {
  return (
    <div className="bg-surface-800/50 rounded-xl p-4">
      <p className="text-surface-200 font-medium text-sm mb-1">{title}</p>
      <p className="text-surface-500 text-sm leading-relaxed">{description}</p>
    </div>
  )
}

function Right({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-1 w-1.5 h-1.5 rounded-full bg-primary-500 shrink-0" />
      <div>
        <span className="text-surface-200 font-medium text-sm">{title}</span>
        <span className="text-surface-500 text-sm"> — {description}</span>
      </div>
    </div>
  )
}
