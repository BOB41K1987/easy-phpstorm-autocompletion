# EasyErrorHandler Translation Navigation (PhpStorm plugin)

Adds **autocompletion** and **Go to Declaration** (`Cmd/Ctrl+B`, `Cmd/Ctrl+Click`) for
EasyErrorHandler exception message keys, resolved live from `translations/messages*.php`.

Triggers on the first argument of:

- `new <Exception>(...)` / `parent::__construct(...)` → `exceptions.*` keys
- `->setUserMessage(...)` → `user_messages.*` keys

The plugin reads the returned-array PSI of every `translations/messages*.php` file under
the project content roots — completion lists keys under the relevant prefix; navigation
jumps to the matching leaf (nested arrays and flat dotted keys are both supported).
No generated metadata is involved, so completion never goes stale. Depends only on the
bundled PHP plugin — the Symfony plugin is **not** required.

It also autocompletes **Zenstruck Foundry factory attribute keys**: inside the array
passed to `new(...)`, `with(...)`, `createEntity(...)`, `makeEntity(...)`, `create(...)`,
`createOne(...)`, `createMany(...)`, `createEntityList(...)`, it offers the property names
of the entity the factory builds. The entity is resolved from the factory's
`class()` method, and its properties (including inherited ones) are walked from PSI —
e.g. `DisputeFactory::new()->createEntity(['<caret>'])` lists `Dispute`'s properties.

And it autocompletes **EntityExpectation `$criteria` keys**: inside the criteria array of
`toBeInDb(...)`, `toNotBeInDb(...)`, `toHaveCountInDb(int, ...)`, it offers the properties of
the entity asserted via `assertEntity(Entity::class)` at the head of the chain — e.g.
`$this->assertEntity(EventLog::class)->toBeInDb(['<caret>'])`. Only the criteria array
parameter is completed (not the trailing `$jsonAttributes` / `$encryptableAttributes` lists).

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
select `build/distributions/phpstorm-eeh-translation-nav-1.1.0.zip` → restart.

## Live development

```bash
./gradlew runIde      # launches a sandbox PhpStorm with the plugin loaded
```
