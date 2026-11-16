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

## Commit messages

A short subject line (at most 72 characters, no full stop) saying what
changed, a blank line, then the why and the details as a body wrapped at
about 72 columns. GitHub cuts a longer subject with "…" and moves the rest
into the pull request's description, which then reads badly. For example:

    Build releases without the Gradle download cache

    What is released is built from the dependencies as their
    repositories have them, not from what another run left behind.
