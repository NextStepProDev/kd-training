import { Link } from 'react-router-dom'
import { Seo } from '../components/seo/Seo'
import { BUSINESS, CONTACT_EMAIL, CONTACT_PHONE } from '../config/site'
import { PageHeader } from '../components/ui/PageHeader'

/**
 * Same date as TermsOfService.CURRENT_VERSION in the backend. A change of the text that matters to
 * anybody moves both: the backend version is what makes everyone accept the terms again, and
 * TermsOfServiceVersionTest fails the build when the two disagree.
 */
const LAST_UPDATED = '12 listopada 2026'

/** How long a client's own photos stay — TrainingPhotoService.RETENTION in the backend, as the privacy policy says too. */
const PHOTO_RETENTION_DAYS = 30

/**
 * Terms of service. Two authors, on purpose: the part about the website (account, rules of use,
 * data, blocking and deletion) is ours and describes what the code does — when the code changes,
 * this text changes with it. The rules of the cooperation itself (cancellations, contraindications,
 * liability) are the coach's, and §8 waits for his text; the banner says so on the page.
 */
export function TermsPage() {
  return (
    <div>
      <Seo
        title="Regulamin"
        description="Regulamin serwisu Mocny Grzbiet — zasady korzystania z konta i strefy podopiecznego."
        path="/regulamin"
      />
      <div className="mx-auto max-w-3xl px-4 pt-14 sm:pt-20">
        <PageHeader kicker="Informacje prawne" title="Regulamin" description="Zasady korzystania z serwisu Mocny Grzbiet." />
      </div>

      {/* max-w-3xl: a legal text is read line by line, and past ~80 characters the eye loses the next line. */}
      <div className="max-w-3xl mx-auto px-4 py-10 sm:py-14 space-y-6">

        <div className="bg-surface-900 border border-surface-700 rounded-2xl p-6 sm:p-8">
          {/* Until the coach sends his rules of cooperation (§8). */}
          <p className="font-semibold text-surface-200">[Wersja robocza — brakuje zasad współpracy z Trenerem (§8)]</p>
          <p className="text-surface-500 text-sm mt-4">
            Ostatnia aktualizacja: {LAST_UPDATED}
          </p>
        </div>

        <Section title="§1. Postanowienia ogólne">
          <Paragraphs items={[
            'Regulamin określa zasady korzystania z serwisu internetowego Mocny Grzbiet (dalej: „Serwis”), w tym rodzaje i zakres usług świadczonych drogą elektroniczną, warunki ich świadczenia, warunki zawierania i rozwiązywania umów oraz tryb postępowania reklamacyjnego.',
            `Usługodawcą jest ${BUSINESS.name}, ${BUSINESS.street}, ${BUSINESS.city}, NIP ${BUSINESS.nip}.`,
          ]} />
          <p className="text-surface-400 text-sm mt-3 leading-relaxed">
            Kontakt z Usługodawcą:{' '}
            <a href={`mailto:${CONTACT_EMAIL}`} className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white transition-colors">
              {CONTACT_EMAIL}
            </a>
            , tel.{' '}
            <a href={CONTACT_PHONE.href} className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white transition-colors">
              {CONTACT_PHONE.display}
            </a>
          </p>
        </Section>

        <Section title="§2. Definicje">
          <Items items={[
            'Użytkownik — osoba, która założyła Konto w Serwisie.',
            'Konto — zbiór danych i ustawień Użytkownika w Serwisie, dostępny po zalogowaniu.',
            'Zaproszenie — jednorazowy link wysłany przez Trenera na adres e-mail osoby, z którą Trener ustalił współpracę; tylko z Zaproszenia można założyć Konto.',
            'Podopieczny — Użytkownik, któremu Trener udostępnił Strefę podopiecznego.',
            'Strefa podopiecznego — część Serwisu z indywidualnym planem treningowym, wiadomościami, zdjęciami, materiałami wideo i celami.',
            'Trening wspólny — trening, w którym bierze udział kilku Podopiecznych jednocześnie.',
            'Trener — Kacper Dąbrowski, prowadzący treningi i plan Podopiecznego.',
          ]} />
        </Section>

        <Section title="§3. Usługi świadczone w Serwisie">
          <Items items={[
            'Prowadzenie Konta — bezpłatnie, przez czas nieokreślony.',
            'Strefa podopiecznego — udostępniana przez Trenera osobom, z którymi ustalił współpracę. Obejmuje kalendarz treningów, wymianę wiadomości i zdjęć z Trenerem, materiały wideo, cele oraz statystyki wykonanych treningów.',
            'Powiadomienia e-mailem — o nowej wiadomości od Trenera oraz o dodaniu, zmianie lub odwołaniu treningu przez Trenera. Powiadomienia nie zawierają treści wiadomości ani opisów treningów — te są widoczne wyłącznie po zalogowaniu.',
            'Zakres, terminy i wynagrodzenie za treningi personalne ustalane są indywidualnie, poza Serwisem. Serwis jest narzędziem do prowadzenia tej współpracy.',
          ]} />
        </Section>

        <Section title="§4. Wymagania techniczne i dostępność">
          <Items items={[
            'Urządzenie z dostępem do internetu i aktualną przeglądarką internetową z włączoną obsługą JavaScript.',
            'Aktywny adres e-mail — potrzebny do założenia Konta z Zaproszenia, odzyskiwania hasła i otrzymywania powiadomień.',
            'Serwis zapisuje w przeglądarce wyłącznie dane niezbędne do działania, np. do utrzymania sesji po zalogowaniu.',
            'Usługodawca dokłada starań, aby Serwis działał bez przerw, jednak może je wprowadzać na czas prac technicznych, aktualizacji lub usuwania awarii.',
          ]} />
        </Section>

        <Section title="§5. Konto">
          <Items items={[
            'Konto można założyć wyłącznie z Zaproszenia, na adres e-mail, na który zostało wysłane. Założenie Konta wymaga podania prawdziwych danych oraz akceptacji Regulaminu i Polityki prywatności.',
            'Konto może założyć osoba, która ukończyła 16 lat. Osoba młodsza może korzystać ze Strefy podopiecznego wyłącznie za pośrednictwem Konta rodzica lub opiekuna prawnego, który odpowiada za podane w nim dane i wyrażone zgody.',
            'Umowa o prowadzenie Konta zostaje zawarta z chwilą jego założenia.',
            'Użytkownik dba o poufność swojego hasła i nie udostępnia Konta innym osobom.',
          ]} />
        </Section>

        <Section title="§6. Strefa podopiecznego">
          <Items items={[
            'Strefę podopiecznego włącza Trener. Przed pierwszym jej otwarciem Podopieczny wyraża odrębną, dobrowolną zgodę na przetwarzanie danych dotyczących zdrowia (ocena wysiłku, limity kalorii, zdjęcia). Bez tej zgody plan treningowy pozostaje zamknięty, a reszta Konta działa normalnie.',
            'Treningi i zadania dodane przez Trenera Podopieczny może oznaczać jako wykonane i komentować, ale nie może ich zmieniać ani usuwać. Wpisy dodane samodzielnie Podopieczny może zmieniać i usuwać.',
            `Zdjęcia przesłane przez Podopiecznego usuwane są automatycznie po ${PHOTO_RETENTION_DAYS} dniach. Materiały przekazane przez Trenera (filmy, wskazówki) pozostają dostępne przez cały czas współpracy.`,
            'Przy Treningu wspólnym pozostali uczestnicy widzą imię Podopiecznego. Wykonanie treningu, ocena wysiłku, komentarze i zdjęcia każdej osoby są widoczne wyłącznie dla niej i Trenera.',
          ]} />
        </Section>

        <Section title="§7. Zasady korzystania">
          <Items items={[
            'Zakazane jest dostarczanie treści o charakterze bezprawnym, w tym naruszających dobra osobiste lub prawa autorskie osób trzecich.',
            'Zdjęcia przesyłane w Strefie podopiecznego mogą przedstawiać wyłącznie Użytkownika albo osoby, które się na to zgodziły.',
            'Plan treningowy i materiały udostępnione przez Trenera są przeznaczone wyłącznie do osobistego użytku Podopiecznego i nie mogą być rozpowszechniane.',
            'Plan treningowy w Serwisie nie zastępuje porady lekarskiej. W razie bólu, zawrotów głowy lub innych niepokojących objawów należy przerwać ćwiczenie i skontaktować się z Trenerem, a w razie potrzeby z lekarzem.',
          ]} />
        </Section>

        <Section title="§8. Zasady współpracy z Trenerem">
          {/* The coach's own text goes here: cancellations and rescheduling, contraindications, his
              liability and the client's. Not ours to write — see the class comment. */}
          <p className="font-semibold text-surface-200">
            [Do uzupełnienia przez Trenera: odwoływanie i przesuwanie treningów, przeciwwskazania,
            odpowiedzialność Trenera i Podopiecznego]
          </p>
        </Section>

        <Section title="§9. Reklamacje">
          <Items items={[
            'Reklamacje dotyczące działania Serwisu można zgłaszać na adres e-mail Usługodawcy, opisując problem i podając adres e-mail przypisany do Konta.',
            'Usługodawca rozpatruje reklamację w terminie 14 dni od jej otrzymania i odpowiada na adres e-mail, z którego ją wysłano.',
          ]} />
        </Section>

        <Section title="§10. Zakończenie współpracy i usunięcie Konta">
          <Items items={[
            'Użytkownik może w każdej chwili usunąć Konto w ustawieniach Serwisu, co jest równoznaczne z rozwiązaniem umowy o prowadzenie Konta. Usunięcie trwale kasuje dane Konta, w tym cały plan treningowy z komentarzami i zdjęciami.',
            'Po zakończeniu współpracy Trener może zablokować Konto. Zablokowane Konto nie pozwala się zalogować, a dane czekają na wypadek powrotu — najdłużej rok od zablokowania, po czym Konto i wszystkie dane usuwane są automatycznie. Wcześniejsze usunięcie następuje na prośbę wysłaną na adres e-mail Usługodawcy.',
            'Trener może też usunąć sam plan treningowy, pozostawiając Konto.',
            'Usługodawca może zablokować lub usunąć Konto, jeżeli Użytkownik rażąco narusza Regulamin, po bezskutecznym wezwaniu do zaprzestania naruszeń.',
          ]} />
        </Section>

        <Section title="§11. Dane osobowe">
          <p className="text-surface-400 leading-relaxed">
            Zasady przetwarzania danych osobowych, w tym prawo do otrzymania kopii swoich danych, opisuje{' '}
            <Link to="/polityka-prywatnosci" className="text-white underline decoration-white/30 underline-offset-4 hover:decoration-white">
              Polityka prywatności
            </Link>.
          </p>
        </Section>

        <Section title="§12. Postanowienia końcowe">
          <Items items={[
            'O zmianie Regulaminu Usługodawca informuje Użytkowników e-mailem co najmniej 14 dni przed jej wejściem w życie. Użytkownik, który nie akceptuje zmian, może usunąć Konto.',
            'Po wejściu zmian w życie Użytkownik akceptuje nową treść Regulaminu przy najbliższym wejściu do Serwisu. Do czasu akceptacji korzystanie z Konta jest wstrzymane, a dane Użytkownika pozostają bez zmian.',
            'W sprawach nieuregulowanych w Regulaminie stosuje się przepisy prawa polskiego.',
            'Postanowienia Regulaminu nie wyłączają ani nie ograniczają uprawnień konsumentów wynikających z bezwzględnie obowiązujących przepisów prawa.',
          ]} />
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

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface-900 border border-surface-800 rounded-2xl p-6 sm:p-8">
      <h2 className="text-display text-2xl text-white mb-5">{title}</h2>
      {children}
    </div>
  )
}

function Paragraphs({ items }: { items: string[] }) {
  return (
    <div className="space-y-3">
      {items.map((item, i) => (
        <p key={i} className="text-surface-400 leading-relaxed">{item}</p>
      ))}
    </div>
  )
}

function Items({ items }: { items: string[] }) {
  return (
    <ol className="space-y-2 list-decimal list-outside pl-5 marker:text-surface-500">
      {items.map((item, i) => (
        <li key={i} className="text-surface-400 text-sm leading-relaxed pl-1">{item}</li>
      ))}
    </ol>
  )
}
