#!/usr/bin/env bash
#
# SEO / crawler smoke test.
#
# Catches the silent indexing failures that unit tests can't reach, because they
# live in nginx config + robots.txt, not in Java:
#   - the nginx bot-UA meta-refresh LOOP (a search crawler wrongly added to a
#     bot list gets bounced <page> -> /og/... -> meta-refresh -> <page> forever
#     -> GSC "Redirect error" -> pages deindexed),
#   - robots.txt fat-fingers (a stray "Disallow: /" blanks the whole site,
#     a dropped "Sitemap:" line hides the map).
#
# Safe & cheap: a handful of read-only GETs from wherever you run it (your laptop
# or a CI runner). It does NOT run in the browser, on the prod server, or inside
# the app process — zero impact on the running app, its RAM, or its speed. It
# just visits a few pages like a human would, then exits.
#
# Usage:  ./seo-smoke.sh BASE_URL       (e.g. https://example.pl — Deploy passes vars.SITE_URL)
# Exit:   0 = all checks passed, 1 = at least one check failed.

set -uo pipefail

BASE="${1:?usage: seo-smoke.sh BASE_URL}"
GOOGLE_UA="Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
FB_UA="facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
FAILED=0

pass() { echo "OK   $1"; }
fail() { echo "FAIL $1"; FAILED=1; }

get() { curl -sS -m 15 "$@"; }

echo "SEO smoke against ${BASE}"
echo "---"

# 1. robots.txt sanity -------------------------------------------------------
ROBOTS=$(get "${BASE}/robots.txt" || true)
if echo "$ROBOTS" | grep -qi "^Sitemap:"; then
  pass "robots.txt advertises a Sitemap:"
else
  fail "robots.txt is missing the Sitemap: line"
fi
# A "Disallow: /" is only dangerous when it sits under "User-agent: *" (blocks
# every crawler). A per-bot "Disallow: /" (e.g. Cloudflare's managed blocks for
# GPTBot/ClaudeBot/CCBot) is intentional and fine, so we must scope the check to
# the "*" group rather than flagging every Disallow line.
if printf '%s\n' "$ROBOTS" | awk '
    function val(s){ sub(/^[^:]*:[[:space:]]*/,"",s); gsub(/[[:space:]]/,"",s); return s }
    { line=$0; sub(/\r$/,"",line) }
    line ~ /^[[:space:]]*#/ { next }
    tolower(line) ~ /^[[:space:]]*user-agent:/ {
        if (prev_rule) star=0          # a rule before this UA means a new group starts
        if (val(line)=="*") star=1
        prev_rule=0; next
    }
    tolower(line) ~ /^[[:space:]]*disallow:/ {
        if (star==1 && val(line)=="/") blocked=1
        prev_rule=1; next
    }
    tolower(line) ~ /^[[:space:]]*allow:/ { prev_rule=1; next }
    line ~ /^[[:space:]]*$/ { prev_rule=1 }
    END { exit blocked?1:0 }
  '; then
  pass "robots.txt allows crawling under User-agent: * (per-bot AI blocks are fine)"
else
  fail "robots.txt blocks ALL crawlers (Disallow: / under User-agent: *)"
fi

# 2. sitemap.xml reachable & well-formed-ish ---------------------------------
SITEMAP=$(get "${BASE}/sitemap.xml" || true)
if echo "$SITEMAP" | grep -q "<urlset"; then
  pass "sitemap.xml served with <urlset>"
else
  fail "sitemap.xml missing or has no <urlset> root"
fi

# 3. THE bot-UA loop guard ---------------------------------------------------
# Today the home page is the only public page with link previews; nginx serves it to every client
# alike (static OG tags in index.html). The check still runs, so a future per-page /og/ rewrite
# that catches a search crawler fails here rather than in Search Console weeks later.
echo "---"

# 3a. Social scraper MUST see OG tags.
FB_BODY=$(get -A "$FB_UA" "${BASE}/" || true)
if echo "$FB_BODY" | grep -qi 'property="og:title"'; then
  pass "social scraper (facebookexternalhit) gets OG tags"
else
  fail "social scraper did NOT get OG tags — link previews broken"
fi

# 3b. Search crawler MUST receive the SPA, never a meta-refresh.
# A meta-refresh served to Googlebot is the exact signature of the bot-UA
# loop that deindexes pages.
G_BODY=$(get -A "$GOOGLE_UA" "${BASE}/" || true)
if echo "$G_BODY" | grep -qi 'http-equiv="refresh"'; then
  fail "search crawler got a meta-refresh — bot-UA LOOP (GSC redirect error risk)"
else
  pass "search crawler (Googlebot) gets the SPA, no meta-refresh loop"
fi

# 4. CSP lets the exercise-video library load -------------------------------
# Same class of failure as the bot loop: it lives in nginx, not in Java, and no
# test can see it, because the Vite dev server sends no CSP at all. A CSP that
# forgets the two YouTube hosts leaves the coach's video library rendering blank
# players and broken posters — on production only, and silently.
echo "---"
CSP=$(curl -sSI -m 15 "${BASE}/" | tr -d '\r' | grep -i '^content-security-policy:' || true)
if [ -z "$CSP" ]; then
  fail "no Content-Security-Policy header on the SPA shell"
else
  if echo "$CSP" | grep -q "img.youtube.com"; then
    pass "CSP img-src allows YouTube posters"
  else
    fail "CSP blocks YouTube posters (img.youtube.com missing from img-src)"
  fi
  # frame-src must be spelled out; falling back to default-src 'self' blocks the player.
  if echo "$CSP" | grep -q "frame-src[^;]*youtube-nocookie.com"; then
    pass "CSP frame-src allows the YouTube player"
  else
    fail "CSP blocks the YouTube player (frame-src youtube-nocookie.com missing)"
  fi
  # The CSP successor of X-Frame-Options, and the one that wins where a browser reads both. Matched
  # to the end of the directive: a widened value ("'self' https://other.example") lets that site frame
  # us and still contains the substring "frame-ancestors 'self'".
  if echo "$CSP" | grep -qE "frame-ancestors 'self'(;|$)"; then
    pass "CSP frame-ancestors forbids framing by other sites"
  else
    fail "CSP lets other sites frame the app (frame-ancestors 'self' missing or widened)"
  fi
fi

echo "---"
if [ "$FAILED" -ne 0 ]; then
  echo "SEO smoke FAILED — see FAIL lines above."
  exit 1
fi
echo "SEO smoke passed: robots, sitemap, crawler routing and CSP all healthy."
