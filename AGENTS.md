# Agent instructions

When changing UI shown in the tutorial, check the corresponding help
screenshots and update them if they no longer match. Update the walkthrough
and screenshot capture test as needed. See the User help section of
`PLAN.md` for the regeneration command.

## Builds alongside editing

Gradle builds and tests the working tree as it is when each task runs, and
a test JVM reads jars that another build in the same tree rewrites (seen
as `ZipException: invalid LOC header`, or tests of half-edited files). So:

- For a full build (`./gradlew build`), the container image or any other
  long build run in the background, use a separate clone of the last
  commit, e.g. `git clone --no-hardlinks . <scratch>/wpsld-build` (or `git
  archive HEAD` for the image), and go on working in this tree. Commit
  first what the build is to include.
- Don't run a second Gradle build in this tree while one runs here.
- While debugging a failure, run the tests in this tree, in the
  foreground, as the changes are made here.

## The conda environment

`environment.yml` may be changed to help the build or the checks: a newer
JDK or tool, for instance when the conda-forge JDK is behind the
release's (its trust store, or a security fix). Apply it with `conda env
update --prefix /workspace/.conda_env -f environment.yml --prune` and make
sure the command succeeds, as the next container start applies it too;
**solve it first as a dry run** (`conda env create --dry-run -p /tmp/x -f
environment.yml`) and check afterwards that `gtk3` and `pango` are still
installed (`conda list`): a package that does not fit is dropped by `--prune`
without an error, and then every UI test fails with `no javafx_font_pango`
(`openjdk>=25.0.5.1` did this: it conflicts with gtk3 over libcups);
keep the comments there about why a package is pinned, and check the
build with the change (`./gradlew build`, in a clone, see below) before
committing it. Never add the `defaults` channel.

The conda-forge JDK's `cacerts` lacks HARICA's 2021 roots (the release's
Temurin has them), so the online tests of sites under GEANT TLS, such as KU
Leuven's, fail here with `PKIX path building failed`, and so does
`theJdkTrustsHaricasRoots`. To run them, use a copy of the JDK's `cacerts`
with the root added from the system's (`keytool -importcert -alias
harica-rsa-2021 -file /etc/ssl/certs/HARICA_TLS_RSA_Root_CA_2021.pem -keystore
<copy> -storepass changeit -noprompt`) as `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=<copy>
-Djavax.net.ssl.trustStorePassword=changeit" ./gradlew onlineTest`.

## Outgoing requests

Every request wpsld makes to the web names wpsld and its version in its
`User-Agent`, `wpsld/<version> (https://github.com/stain/wpsld)`, from
`no.s11.wpsld.util.UserAgent.get()`, as RFC 9110 (10.1.5) has it and
sites such as OpenStreetMap, GeoNames and ORCID ask. A new client or
request sets `.header("User-Agent", UserAgent.get())`; no constructor takes
an agent to be given another. The JDK's `HttpClient` has no default
headers, so add a test beside `TestUserAgent` that records the header, for
whatever is new. Never retry a refused (403) or blocked (a bot wall)
request with other headers.

## Commit messages

A short subject line (at most 72 characters, no full stop) saying what
changed, a blank line, then the why and the details as a body wrapped at
about 72 columns. GitHub cuts a longer subject with "…" and moves the rest
into the pull request's description, which then reads badly. For example:

    Build releases without the Gradle download cache

    What is released is built from the dependencies as their
    repositories have them, not from what another run left behind.
