# EasyErrorHandler Translation Navigation (PhpStorm plugin)

Every completion this plugin contributes is marked with an EonX icon, so it's clear which
suggestions come from here versus PhpStorm/the Symfony plugin.

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

It also fills a Symfony-plugin gap: **Go to Declaration for constraint `message` keys written
as attributes** — e.g. `#[Assert\Expression(message: '<caret>')]` — navigating to the
`validators` domain (`translations/validators*.php`). The Symfony plugin already handles
completion and goto for the `new Assert\…(message: …)` and `trans()` forms, and completion for
the attribute form, but not goto for the attribute form. Applies to any argument whose name
ends in `message` (`message`, `minMessage`, `maxMessage`, …) on a class under
`Symfony\Component\Validator\Constraints\` or extending `Validator\Constraint`.

It also autocompletes **Zenstruck Foundry factory attribute keys**: inside the array
passed to `new(...)`, `with(...)`, `createEntity(...)`, `makeEntity(...)`, `create(...)`,
`createOne(...)`, `createMany(...)`, `createEntityList(...)`, it offers the property names
of the entity the factory builds. The entity is resolved from the factory's
`class()` method, and its properties (including inherited ones) are walked from PSI —
e.g. `DisputeFactory::new()->createEntity(['<caret>'])` lists `Dispute`'s properties. The
same keys are offered inside the array returned by the factory's own `defaults()` method.

It autocompletes **`setMessageParams` / `setUserMessageParams` keys** with the ICU placeholders
(`{name}`) declared in the corresponding message — the constructor message for `setMessageParams`,
the `setUserMessage(...)` message for `setUserMessageParams` — and **underlines the message key in
yellow** when it declares placeholders not provided in the matching params call, with an
**Alt+Enter quick-fix** that adds the missing placeholders (merging into an existing params array
or appending the params call on the exception variable, scaffolding values as `null`).

And it autocompletes **EntityExpectation `$criteria` keys**: inside the criteria array of
`toBeInDb(...)`, `toNotBeInDb(...)`, `toHaveCountInDb(int, ...)`, it offers the properties of
the entity asserted via `assertEntity(Entity::class)` at the head of the chain — e.g.
`$this->assertEntity(EventLog::class)->toBeInDb(['<caret>'])`.

The trailing `$jsonAttributes` / `$encryptableAttributes` **list values** of `toBeInDb` /
`toNotBeInDb` are completed too, scoped to the keys present in that call's first `$criteria`
array — e.g. `->toBeInDb(['type' => ..., 'payload' => ...], ['<caret>'])` offers `type`, `payload`.

It also **annotates** `toBeInDb` / `toNotBeInDb` for JSONB consistency:

- a criteria key that maps to an `#[ORM\Column(type: JsonbType::NAME)]` property but is missing
  from `$jsonAttributes` is highlighted with a yellow background, and offers an **Alt+Enter
  quick-fix** ("Add '…' to $jsonAttributes") that adds the key — creating the `$jsonAttributes`
  argument if the call doesn't have one;
- a `$jsonAttributes` value that is not a key of `$criteria` is underlined in yellow.

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
