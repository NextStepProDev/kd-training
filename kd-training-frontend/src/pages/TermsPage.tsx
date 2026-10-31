import { Link } from 'react-router-dom'
import { Seo } from '../components/seo/Seo'
import { CONTACT_EMAIL } from '../config/site'

const LAST_UPDATED = '31 października 2026'

/**
 * Terms of service. A working draft: the mechanics around it (acceptance at registration and on the
 * profile-completion screen, the stored moment, the GDPR export) are final, the wording is not — it
 * waits for the client's company details and a legal review. The banner below says so on the page
 * itself, the same way the privacy policy marks its own placeholders.
 */
export function TermsPage() {
  return (
    <div className="min-h-screen bg-surface-950">
      <Seo
        title="Regulamin"
        description="Regulamin serwisu KD Training — zasady korzystania z konta i strefy podopiecznego."
        path="/regulamin"
      />
      <div className="relative overflow-hidden bg-gradient-to-b from-surface-900 to-surface-950 border-b border-surface-800">
        <div className="relative max-w-4xl mx-auto px-4 py-16 sm:py-24 text-center">
          <h1 className="text-3xl sm:text-4xl font-bold text-surface-100 mb-3">
            Regulamin
          </h1>
          <p className="text-surface-400 text-lg max-w-xl mx-auto">
            Zasady korzystania z serwisu KD Training.
          </p>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-12 sm:py-16 space-y-6">

        <div className="bg-surface-900 border border-surface-700 rounded-2xl p-6 sm:p-8">
          {/* Placeholder until the client's details and a legal review are in. */}
          <p className="font-semibold text-surface-200">[Wersja robocza — treść do weryfikacji]</p>
          <p className="text-surface-500 text-sm mt-4">
            Ostatnia aktualizacja: {LAST_UPDATED}
          </p>
        </div>

        <Section title="§1. Postanowienia ogólne">
          <Paragraphs items={[
            'Regulamin określa zasady korzystania z serwisu internetowego KD Training (dalej: „Serwis”), w tym rodzaje i zakres usług świadczonych drogą elektroniczną, warunki ich świadczenia oraz tryb postępowania reklamacyjnego.',
            'Usługodawcą jest: [Dane usługodawcy — do uzupełnienia].',
          ]} />
          <p className="text-surface-400 text-sm mt-3 leading-relaxed">
            Kontakt z Usługodawcą:{' '}
            <a href={`mailto:${CONTACT_EMAIL}`} className="text-primary-400 hover:text-primary-300 transition-colors">
              {CONTACT_EMAIL}
            </a>
          </p>
        </Section>

        <Section title="§2. Definicje">
          <Items items={[
            'Użytkownik — osoba, która założyła Konto w Serwisie.',
            'Konto — zbiór danych i ustawień Użytkownika w Serwisie, dostępny po zalogowaniu.',
            'Podopieczny — Użytkownik, któremu Trener udostępnił Strefę podopiecznego.',
            'Strefa podopiecznego — część Serwisu z indywidualnym planem treningowym, wiadomościami, zdjęciami, materiałami wideo i celami.',
            'Trener — Kacper Dąbrowski, prowadzący treningi i plan Podopiecznego.',
          ]} />
        </Section>

        <Section title="§3. Usługi świadczone w Serwisie">
          <Items items={[
            'Prowadzenie Konta — bezpłatnie, przez czas nieokreślony.',
            'Strefa podopiecznego — udostępniana przez Trenera osobom, z którymi ustalił współpracę; obejmuje kalendarz treningów, wymianę wiadomości i zdjęć, materiały wideo oraz cele.',
            'Zakres, terminy i wynagrodzenie za treningi personalne ustalane są indywidualnie, poza Serwisem. Serwis jest narzędziem do prowadzenia tej współpracy.',
          ]} />
        </Section>

        <Section title="§4. Wymagania techniczne">
          <Items items={[
            'Urządzenie z dostępem do internetu i aktualną przeglądarką internetową z włączoną obsługą JavaScript.',
            'Aktywny adres e-mail — potrzebny do aktywacji Konta i odzyskiwania hasła.',
            'Serwis zapisuje w przeglądarce wyłącznie dane niezbędne do działania, np. do utrzymania sesji po zalogowaniu.',
          ]} />
        </Section>

        <Section title="§5. Konto">
          <Items items={[
            'Założenie Konta wymaga podania prawdziwych danych, akceptacji Regulaminu oraz Polityki prywatności i aktywacji Konta linkiem wysłanym na adres e-mail.',
            'Użytkownik dba o poufność swojego hasła i nie udostępnia Konta innym osobom.',
            'Umowa o prowadzenie Konta zostaje zawarta z chwilą jego aktywacji.',
          ]} />
        </Section>

        <Section title="§6. Zasady korzystania">
          <Items items={[
            'Zakazane jest dostarczanie treści o charakterze bezprawnym, w tym naruszających dobra osobiste lub prawa autorskie osób trzecich.',
            'Zdjęcia przesyłane w Strefie podopiecznego mogą przedstawiać wyłącznie Użytkownika albo osoby, które się na to zgodziły.',
            'Plan treningowy i materiały udostępnione przez Trenera są przeznaczone wyłącznie do osobistego użytku Podopiecznego i nie mogą być rozpowszechniane.',
          ]} />
        </Section>

        <Section title="§7. Zdrowie i bezpieczeństwo">
          <Items items={[
            'Plan treningowy nie zastępuje porady lekarskiej. Przed rozpoczęciem treningów warto skonsultować się z lekarzem, zwłaszcza w razie chorób, urazów lub ciąży.',
            'Podopieczny informuje Trenera o znanych mu przeciwwskazaniach, urazach i dolegliwościach mogących mieć wpływ na trening.',
            'W razie bólu, zawrotów głowy lub innych niepokojących objawów należy przerwać ćwiczenie i skontaktować się z Trenerem, a w razie potrzeby z lekarzem.',
          ]} />
        </Section>

        <Section title="§8. Reklamacje">
          <Items items={[
            'Reklamacje dotyczące działania Serwisu można zgłaszać na adres e-mail Usługodawcy, opisując problem i podając adres e-mail przypisany do Konta.',
            'Usługodawca rozpatruje reklamację w terminie 14 dni od jej otrzymania i odpowiada na adres e-mail, z którego ją wysłano.',
          ]} />
        </Section>

        <Section title="§9. Usunięcie konta">
          <Items items={[
            'Użytkownik może w każdej chwili usunąć Konto w ustawieniach Serwisu, co jest równoznaczne z rozwiązaniem umowy o prowadzenie Konta.',
            'Usługodawca może zablokować lub usunąć Konto, jeżeli Użytkownik rażąco narusza Regulamin, po bezskutecznym wezwaniu do zaprzestania naruszeń.',
          ]} />
        </Section>

        <Section title="§10. Dane osobowe">
          <p className="text-surface-400 leading-relaxed">
            Zasady przetwarzania danych osobowych opisuje{' '}
            <Link to="/polityka-prywatnosci" className="text-primary-400 hover:text-primary-300 underline">
              Polityka prywatności
            </Link>.
          </p>
        </Section>

        <Section title="§11. Postanowienia końcowe">
          <Items items={[
            'O zmianie Regulaminu Usługodawca informuje Użytkowników e-mailem co najmniej 14 dni przed jej wejściem w życie. Użytkownik, który nie akceptuje zmian, może usunąć Konto.',
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
      <h2 className="text-xl font-semibold text-surface-100 mb-5">{title}</h2>
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
