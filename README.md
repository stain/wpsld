# wpsld

wpsld is a desktop editor for [RO-Crate](https://w3id.org/ro/crate/1.3)
metadata. It describes files and folders with schema.org-based Linked Data
and saves the result as `ro-crate-metadata.json`. It is a lightweight
desktop take on [Crate-O](https://github.com/Language-Research-Technology/crate-o)
and Describo.

- Forms are built from schema.org 30.0, the version RO-Crate 1.3 uses.
- Forms can be narrowed by RO-Crate MASP profiles (machine-actionable
  profiles). The core RO-Crate profile is bundled. Other profiles load from
  a URL, a file, the [MASP catalogue](https://language-research-technology.github.io/ro-crate-masp/)
  or [profiles.ro-crate.org](https://profiles.ro-crate.org/).
- Written in Java 25 with JavaFX 27.

It is a work in progress. See [PLAN.md](PLAN.md) for the plan and
[MASP.md](MASP.md) for how profiles are read and used.

## Running a release

Download a zip from the [releases](https://github.com/stain/wpsld/releases).
The zips for a platform include Java and JavaFX, so nothing else needs to
be installed.

| Zip | Platform | Start |
|-----|----------|-------|
| `wpsld-<version>-linux-x64.zip` | Linux x64 | `wpsld/bin/wpsld` |
| `wpsld-<version>-linux-aarch64.zip` | Linux arm64 | `wpsld/bin/wpsld` |
| `wpsld-<version>-osx-aarch64.zip` | macOS, Apple silicon | `wpsld.app` |
| `wpsld-<version>-osx-x64.zip` | macOS, Intel | `wpsld.app` |
| `wpsld-<version>-windows-x64.zip` | Windows x64 | `wpsld\wpsld.exe` |
| `wpsld-<version>-portable.zip` | any, with your own Java 25 and JavaFX 27 | `bin/wpsld`, `bin\wpsld.bat` |

### The apps are not signed yet

Windows and macOS mark files downloaded from the internet, and the mark
carries over to files unzipped from them. As wpsld is not signed, they then
refuse to start it the first time.

**Windows:** Microsoft Defender SmartScreen says it "prevented an
unrecognised app from starting". Either:

- before unzipping, right-click the zip, choose Properties, tick
  **Unblock** and press OK (or in PowerShell:
  `Unblock-File wpsld-<version>-windows-x64.zip`), or
- in the SmartScreen message, click **More info**, then **Run anyway**.

**macOS:** macOS says that "wpsld" can't be opened, or that Apple could not
check it for malicious software. Either:

- in Terminal, in the folder where you unzipped it:

      xattr -dr com.apple.quarantine wpsld.app

- or try to open it once, then go to System Settings > Privacy & Security,
  and click **Open Anyway** beside the message about wpsld.

The portable zip leaves out JavaFX. Either:

- run it with a JDK that includes JavaFX, such as BellSoft Liberica JDK
  "Full" or Azul Zulu "JDK FX", or
- copy the `lib/` files of the [JavaFX 27 SDK](https://gluonhq.com/products/javafx/)
  into its `lib/` folder.

### Where wpsld keeps its files

wpsld keeps profiles you have loaded, and its settings (Edit > Preferences),
in these places:

| | Profiles | Settings |
|-|----------|----------|
| Linux | `~/.local/share/wpsld/profiles/` | `~/.config/wpsld/settings.json` |
| macOS | `~/Library/Application Support/wpsld/profiles/` | `~/Library/Application Support/wpsld/settings.json` |
| Windows | `%APPDATA%\wpsld\profiles\` | `%APPDATA%\wpsld\settings.json` |

On Linux, `$XDG_DATA_HOME` and `$XDG_CONFIG_HOME` are used instead if set.
You can remove kept profiles under Edit > Preferences > Kept profiles.

## Building

You need a JDK 25 or later. The Gradle wrapper downloads Gradle, and
JavaFX comes from Maven Central.

    ./gradlew build        # compile and run the tests
    ./gradlew :ui:run      # start the editor

The GUI tests run headless, with JavaFX's headless platform, so they need
no display. On Linux, JavaFX needs GTK 3 and the X11/GL libraries.

### With conda

[`environment.yml`](environment.yml) gives OpenJDK 25 and the libraries
JavaFX needs on Linux:

    conda env create --prefix ./.conda_env -f environment.yml
    conda run -p ./.conda_env ./gradlew build

### Zips with Java and JavaFX included

These are built with `jlink`. JavaFX must be linked from its jmods, not
its jars, so that its native libraries end up in the image. Download the
[JavaFX 27 jmods](https://gluonhq.com/products/javafx/) for your platform.
Then, with an [Eclipse Temurin](https://adoptium.net/) 25 JDK:

    ./gradlew -Pplatform=linux-x64 -PjavafxJmods=/path/to/javafx-jmods-27.0.1 :ui:appImageZip

This makes `ui/build/image-zip/wpsld-<version>-linux-x64.zip`, an app image
with a native launcher from `jpackage`. `jpackage` can only build for the
platform it runs on. (conda-forge's OpenJDK 25 can't `jlink` yet; see
`environment.yml`.)

The [`Dockerfile`](Dockerfile) builds and tests with Temurin. It also
cross-links Linux and Windows x64 images with shell and `.bat` launchers,
into `dist/`:

    podman build --network=host --output type=local,dest=dist .

The portable zip, without JavaFX, is `./gradlew :ui:distZip`, written to
`ui/build/distributions/`.

### Releases

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds the
app image zips on each platform's GitHub runner, plus the portable zip.
Pushing a tag `v<version>`, e.g. `v0.2.0`, builds that version and makes a
draft GitHub release, to check and publish. Run by hand from the Actions
tab, the workflow only keeps the zips as artifacts. The version otherwise
comes from [`gradle.properties`](gradle.properties).

## Project layout

| Module | Contents |
|--------|----------|
| [`lib`](lib) (`no.s11.wpsld`) | No JavaFX. The schema.org model, form fields, the RO-Crate model, reading and retrieving crates, JSON-LD contexts, MASP profiles and registries, and settings. Its only dependency is Jackson. |
| [`ui`](ui) (`no.s11.wpsld.ui`) | The JavaFX editor: the crate window (`CrateEditor`), the entity form (`EntityEditor`), the preview, the type chooser and the preferences. |

## Licence

[Apache License 2.0](LICENSE.txt)
