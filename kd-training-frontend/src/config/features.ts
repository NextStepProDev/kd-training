/**
 * Features that are built but not switched on yet.
 *
 * A flag lives here only while something is genuinely mid-flight. It is not a settings system and
 * nothing should accumulate: once a feature is live, the flag and every branch it guards go away.
 */

/**
 * Google sign-in.
 *
 * The backend half exists (OAuth2 user service, success handler, the consent screen at
 * `/uzupelnij-profil`) and is covered by tests, but production runs without the `oauth2` profile and
 * without Google credentials — so `/oauth2/authorization/google` answers 401 there. The button was
 * never gated behind anything, which meant a live sign-in page offering a door that opened onto an
 * error. This flag is that gate, and it also drives the "not yet active" markers in the privacy
 * policy, so the page and the promise can never disagree.
 *
 * TO SWITCH IT ON — in this order, or the backend will not start:
 *   1. Create OAuth credentials in the Google Cloud console and register the redirect address
 *      `https://kdtraining.example/login/oauth2/code/google`.
 *   2. Put `OAUTH2_GOOGLE_CLIENT_ID` and `OAUTH2_GOOGLE_CLIENT_SECRET` in the production `.env`.
 *      The `oauth2` profile resolves both without a default, so enabling the profile while they are
 *      missing takes the whole application down, not just the login.
 *   3. Only then set `SPRING_PROFILES_ACTIVE=prod,oauth2`.
 *   4. Flip this to `true` and bump `LAST_UPDATED` in `PrivacyPolicyPage` — the policy already
 *      describes the feature in full, and the flag is what removes the "w przygotowaniu" markers.
 *
 * The two audit findings that used to sit on this path are closed: nginx sets `X-Forwarded-Host`
 * itself and `redirect-uri` is pinned to `${app.site-url}`, so the callback address can no longer be
 * bent by a request header; and the tokens come back in the URL fragment instead of the query
 * string, so they stay out of the proxy logs. Whatever `app.site-url` resolves to has to be
 * registered in the Google console, exactly.
 */
export const GOOGLE_LOGIN_ENABLED = false
