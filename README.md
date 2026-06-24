# EasyErrorHandler Translation Navigation (PhpStorm plugin)

Adds **Go to Declaration** (`Cmd/Ctrl+B`, `Cmd/Ctrl+Click`) from an EasyErrorHandler
exception message key to its definition in `translations/messages*.php`.

Triggers on the first argument of:

- `new <Exception>(...)` / `parent::__construct(...)` → `exceptions.*` keys
- `->setUserMessage(...)` → `user_messages.*` keys

The plugin resolves the key by walking the returned array PSI of every
`translations/messages*.php` file under the project content roots (nested arrays and
flat dotted keys are both supported). It depends only on the bundled PHP plugin — the
Symfony plugin is **not** required.

## Build

Requires JDK 17+ and Gradle (the build downloads the PhpStorm SDK on first run).

```bash
# from this directory
gradle wrapper --gradle-version 8.10.2     # once, generates ./gradlew
./gradlew buildPlugin                       # builds build/distributions/*.zip
```

Build against a specific PhpStorm version:

```bash
./gradlew buildPlugin -PplatformVersion=2025.2.3
```

## Install

PhpStorm → **Settings → Plugins → ⚙ → Install Plugin from Disk…** →
select `build/distributions/phpstorm-eeh-translation-nav-1.0.0.zip` → restart.

## Live development

```bash
./gradlew runIde      # launches a sandbox PhpStorm with the plugin loaded
```
