import { Outlet, useLocation, Navigate } from 'react-router-dom'
import { Navbar } from './Navbar'
import { Footer } from './Footer'
import { GlobalLoadingBar } from '../ui/GlobalLoadingBar'
import { useAuth } from '../../context/AuthContext'
import { needsProfileCompletion } from '../../utils/profileCompletion'

// Routes available to a logged-in user who hasn't completed their account yet (missing data or
// privacy policy not accepted). Everything else is blocked until acceptance
// — GDPR: without consent to the policy we don't allow using the app.
const COMPLETION_ALLOWED_PATHS = ['/uzupelnij-profil', '/polityka-prywatnosci', '/oauth-callback']

export function Layout() {
  const location = useLocation()
  const isHome = location.pathname === '/'
  const { user, isLoading } = useAuth()

  // Hard gate: until the account is completed we redirect to the completion page (which also has
  // the "I don't accept — delete account" option). We wait for the user to load to avoid a redirect flash.
  if (!isLoading && needsProfileCompletion(user) && !COMPLETION_ALLOWED_PATHS.includes(location.pathname)) {
    return <Navigate to="/uzupelnij-profil" replace />
  }

  return (
    <div className="min-h-screen flex flex-col">
      <GlobalLoadingBar />
      {!isHome && <Navbar />}
      <main className="flex-1">
        <div key={location.pathname}>
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  )
}
