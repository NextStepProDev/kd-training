# Runtime only. This image PACKAGES the bundle CI built and tested; it does not build one.
# It used to run `COPY . .` + `npm run build` in a `FROM node:26-alpine` stage, so the bundle
# reaching production was compiled a second time, on a Node the suite never ran on (the workflow
# pinned 24), and nothing executed the result before it shipped. dist/ now arrives from the job that
# tested it, as a workflow artifact (see .github/workflows/ci-frontend.yml), built on the single Node
# version named in .nvmrc.
#
# Consequence for a manual build on a laptop: produce dist/ first, the image expects it here.
#   nvm use && npm ci && npm run build && docker build -t kd-training-frontend .
# `npm run build` is `tsc -b && vite build && node scripts/prerender.mjs` — the prerender step is
# what puts a static <head> on the listing routes, so a hand-rolled `vite build` ships less.
FROM nginx:alpine
# Same reasoning as the backend image: the base ships whatever Alpine packages it was built with and
# is rebuilt on its own schedule, so fixes already published in the Alpine repo sit unapplied until
# then. The CVE gate in CI fails on anything fixable, and waiting for an upstream rebuild is not a
# plan. Nothing here is pinned by name, so tomorrow's advisory needs no edit to this line.
RUN apk upgrade --no-cache

COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
