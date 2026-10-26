# Runtime only. This image PACKAGES the jar the CI suite ran against; it does not compile one.
# It used to do both: a builder stage ran `./gradlew bootJar -x test`, so the artifact the tests
# proved something about and the artifact that reached production were two different compilations
# on two different machines, and nothing ever executed the second one. The jar now arrives from the
# job that tested it, as a workflow artifact (see .github/workflows/ci-backend.yml).
#
# Consequence for a manual build on a laptop: produce the jar first, the image expects it here.
#   ./gradlew clean bootJar && cp build/libs/*.jar app.jar && docker build -t kd-training-backend .
# `clean` is not decoration — build/libs keeps one jar per version ever built, so `cp *.jar` without
# it copies whichever the shell glob happens to hand over.
FROM eclipse-temurin:25-jre-alpine
# curl is for the container healthcheck. The upgrade is for the CVE gate in CI: the base image ships
# whatever Alpine packages it was built with, and it is rebuilt on its own schedule, not ours — so
# fixes that already exist in the Alpine repo can sit unapplied here for weeks. Upgrading everything
# on top of the base pulls those in at build time and keeps the image current between base bumps.
# `apk upgrade` (not a list of package names) on purpose: naming today's offenders means coming back
# to edit this line for tomorrow's, which is the maintenance nobody does.
RUN apk upgrade --no-cache && apk add --no-cache curl
WORKDIR /app

# Stays ABOVE the addgroup/chown block below: that chown is what makes the jar owned by the user
# the container runs as. Move this line under it and the jar stays root-owned, silently.
COPY app.jar app.jar

# The image is safe on its own, not only when compose remembers to say so. This used to read
# "docker" — a profile no application-docker.yml has ever defined — so anything starting the image
# without compose (a debug run on the box, a new environment, somebody new to the project) silently
# got NONE of the production settings: pool of 8 instead of 5, no max-connections guarding memory,
# chatty logging, and CORS left at http://localhost:*. Nothing warned. compose still overrides this
# with ${SPRING_PROFILES_ACTIVE:-prod}; the default is now the same answer rather than a trap.
ENV SPRING_PROFILES_ACTIVE=prod

# Run as an unprivileged user (uid pinned to 1001 so the volume chown is deterministic).
# /app/uploads is pre-created and handed to that user: a FRESH named volume inherits the ownership,
# so a new deployment works with no manual chown. An EXISTING (root-owned) volume needs this once,
# before starting this image:
#   docker run --rm -v kd_uploads_data_prod:/u alpine chown -R 1001:1001 /u
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app \
    && mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8083
# JVM tuning for a ~1GB RAM / 2 vCPU box (mem_limit 384m — see INFRA.md "Serwer produkcyjny"):
# - MaxRAMPercentage=55 / MaxMetaspaceSize=128m -> heap+non-heap stays under the limit;
#   ExitOnOutOfMemoryError -> clean restart (compose restart: unless-stopped) over a wedged JVM.
# - TieredStopAtLevel=1 -> C1-only JIT: skips the C2 optimizing compiler. On 2 vCPU the C2
#   threads contend with the startup thread for the cores, stretching boot; dropping C2 hands
#   those cores back to startup, ~halving the boot-time 502 window on deploy (CPU is idle here,
#   RAM is the bottleneck). Cost is lower peak throughput of hot paths — irrelevant at this load
#   and a one-flag revert if traffic grows. (CDS evaluated and dropped: ~4% gain wasn't worth
#   the build-time no-DB training run breaking silently on future Spring upgrades.)
# GC left to ergonomics (SerialGC on <2GB). $JAVA_OPTS kept as passthrough.
# user.timezone=Europe/Warsaw: monthly billing, payment windows and session dates are driven by
# LocalDate.now()/YearMonth.now() — on the default UTC clock the backend would disagree with Polish
# clients (and the browser) for up to 2h around midnight and at month boundaries.
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 -Duser.timezone=Europe/Warsaw $JAVA_OPTS -jar app.jar"]
