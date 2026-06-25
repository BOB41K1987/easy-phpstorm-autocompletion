# Architecture & continuation guide

Internal notes for extending this PhpStorm plugin. (User-facing feature list lives in `README.md`.)

## What this plugin is

A PhpStorm plugin that adds project-specific IDE assistance for EonX PHP test/error conventions:
completion, Go to Declaration, annotations (warnings) and quick-fixes around translation keys,
Foundry factories, and DB-assertion helpers. It depends only on the bundled PHP plugin
(`com.jetbrains.php`) — **not** the Symfony plugin. One install works across all PHP projects
(mastercard-pba-transact-api-v1, payment-gateway-api-v3, …); detection is by method name / PSI
shape, never by project.

## Build / test / install (this machine has no JDK by default)

The machine has **no JDK out of the box**. Run every Gradle command under **OpenJDK 17**:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"   # brew install openjdk@17
./gradlew test --no-daemon --console=plain        # headless functional tests (the regression guard)
./gradlew buildPlugin --no-daemon --console=plain  # -> build/distributions/phpstorm-eeh-translation-nav-<version>.zip
```

Toolchain (must stay coherent — see the version matrix below):
- **Gradle 9.6** (wrapper) — required by the IntelliJ Platform Gradle Plugin 2.16; runs fine on JDK 17.
- **IntelliJ Platform Gradle Plugin 2.16.0** — needed to resolve the 2025.3 module-based SDK layout
  (`lib/modules/*.jar`); 2.1.0 could NOT (failed with "Could not find …/lib/modules/…jar"). 2.16 in turn
  requires Gradle 9.0+.
- **Kotlin Gradle plugin 2.2.0** — Gradle-9 compatible (2.0.21 was paired with Gradle 8).
- **`platformVersion=2025.3.3`** (gradle.properties) — build against the version teammates run.
- since-build 241, open upper bound; `jvmToolchain(17)` → Java-17 bytecode, loads on PhpStorm 2024.1+.

First `buildPlugin`/`test` downloads the PhpStorm SDK (~1.5 GB, cached after).

**Marketplace compatibility gotcha:** Marketplace derives "compatible builds" from the platform the
plugin was *compiled against*, not just the open `<idea-version>`. A plugin compiled against 2024.3 was
flagged "Not compatible with PhpStorm 2025.3" even with an open until-build — fixed by building against
2025.3.3. So: bump `platformVersion` to the latest your team runs before publishing.

Install: Settings → Plugins → ⚙ → Install Plugin from Disk → the zip → restart.

### Workflow for a new feature

1. Add the contributor/handler/annotator + register it in `src/main/resources/META-INF/plugin.xml`.
2. Bump `version` in **both** `build.gradle.kts` and `plugin.xml`.
3. Add a headless test (see below). Run `./gradlew test`.
4. `./gradlew buildPlugin`, then one commit per feature (history is one-feature-per-commit).

## Source layout (`src/main/kotlin/com/eonx/eeh/translationnav/`)

Shared helpers (reuse these — most features are thin glue over them):

- **ArrayKeyContext** — array-literal key/value plumbing: `keyLiteral`, `enclosingArray`,
  `isKeyPosition`/`isValuePosition`, `existingKeys`/`stringValues` (PSI **unioned with a bracket-depth
  text scan** — see gotcha #3), `typedPrefix`, and the `APPEND_ARROW` insert handler (`'key' => `).
- **PhpEntityUtil** — `resolveClasses` (type → PhpClass, via `.type.global(project)`),
  `resolveClassConstant` (`Entity::class` → PhpClass), `propertiesOf` (entity + ancestors, fields only),
  `jsonbProperties` (fields whose `#[ORM\Column]` references `JsonbType`).
- **TranslationKeyResolver** — reads `translations/<domain>*.php` (domain defaults to `messages`;
  `validators` also used): `resolve` (key → leaf PSI for goto), `collectKeys` (all dotted keys),
  `messageText` (the value text, for placeholder extraction).
- **MessageKeyContext** — `requiredPrefix(literal)`: `exceptions.` for a `new`/`__construct` arg0,
  `user_messages.` for a `setUserMessage` arg0, else null.
- **EntityKeyContext** — `entityClasses(array, project)`: single source of truth resolving the
  entity whose fields are the valid keys for an attribute/criteria array, across all three call
  shapes (factory calls + `defaults()`, EntityExpectation chains, DatabaseEntityTrait helpers).
  Used by both the attribute-key completion and the field goto handler.
- **EntityExpectationContext** — walks an `assertEntity(Entity::class)->…` chain to its entity.
- **ExceptionMessageParamsContext** — pairs `setMessageParams`/`setUserMessageParams` with the
  constructor / setUserMessage message; extracts ICU `{placeholder}` names.
- **ConstraintMessageContext** — detects a Symfony constraint attribute `message`-like named arg.
- **PluginIcons.EONX** — the eonx.com favicon (`/icons/eonx.png` + `@2x`), put on every completion.

Features (each registered in plugin.xml):

| Area | Classes |
|-|-|
| Exception message keys (`exceptions.*`/`user_messages.*`) | ExceptionTranslationKeyCompletionContributor, ExceptionTranslationKeyGotoHandler |
| Entity attribute/criteria **keys** — factory attrs & `defaults()`, EntityExpectation criteria, DatabaseEntityTrait helpers | EntityAttributeKeyCompletionContributor (completion) + EntityFieldGotoHandler (Cmd+B → entity field), both over EntityKeyContext; FactoryEntityResolver |
| EntityExpectation / DatabaseEntityTrait jsonAttributes list **values** | EntityCriteriaListValueCompletionContributor (its own spec map) |
| EntityExpectation JSONB consistency | EntityCriteriaJsonbAnnotator (+ AddJsonAttributeQuickFix) |
| Constraint attribute `message` (validators domain) | ConstraintMessageGotoHandler (completion intentionally NOT added — Symfony plugin provides it) |
| Message placeholders | ExceptionMessageParamsCompletionContributor, ExceptionMessageParamsAnnotator (+ AddMessageParamsQuickFix) |

## PSI gotchas (learned the hard way — check these first when something "doesn't fire")

1. **Array elements are wrapped.** An array element's `StringLiteralExpression` parent is an
   intermediate `PhpPsiElement`, *not* the `ArrayCreationExpression`/`ArrayHashElement`. Always locate
   the array/hash with `PsiTreeUtil.getParentOfType`, never `literal.parent`. Same for a hash element's
   key: `getKey()` unwraps, but `key.parent` is the wrapper — use `getParentOfType(key, ArrayHashElement)`.
2. **Call arguments are NOT wrapped.** A string that is a direct call argument has `ParameterList` as
   its parent (no wrapper). That's why message-key detection uses `literal.parent as? ParameterList`.
3. **Missing-comma parse break.** While typing a new array key *before* an existing entry, the absent
   comma truncates the `ArrayCreationExpression` PSI and the trailing entries vanish from the tree.
   `ArrayKeyContext` recovers existing keys/values with a text scan from the `[` (bracket-depth aware),
   unioned with the PSI result.
4. **Completion dummy.** Inside completion, the caret string contains `CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED`
   ("IntellijIdeaRulezzz"). Use `typedPrefix` (text before the dummy) as the prefix matcher so insertion
   replaces the whole typed content, and filter it out of existing-key sets.
5. **Chained-call type inference.** Resolve a chained qualifier's type via `.type.global(project)`
   (raw `.type.types` may hold unresolved method-return signatures).

## Testing

Headless functional tests via `BasePlatformTestCase` (JUnit3-style `testXxx` methods), with the
bundled PHP plugin loaded. Pattern: `myFixture.addFileToProject(...)` for support files (entities,
traits, `translations/<domain>*.php`), `configureByText` with a `<caret>`, then:

- completion: `myFixture.completeBasic()` → `myFixture.lookupElementStrings`.
- annotations: `myFixture.doHighlighting(HighlightSeverity.WARNING)` → `.description`.
- quick-fixes: `myFixture.availableIntentions.first { it.text.contains(...) }` → `myFixture.launchAction(it)` → assert `myFixture.file.text`.
- icon: render a `LookupElementPresentation` and assert `presentation.icon === PluginIcons.EONX`.

**Diagnostic technique:** to inspect real PSI, write a throwaway test that builds a `StringBuilder`
and `fail(it)` — the dump lands in `build/test-results/test/*.xml` (unescape HTML to read). Delete it
after. This is how every PSI gotcha above was found; reach for it before guessing.

## Conventions

- Detection is syntactic and usually scoped to the enclosing call/function (matches the
  one-thing-per-factory/method patterns in these repos). A value built via a variable or across methods
  generally won't resolve — that's intentional (no guessing) rather than a bug.
- Every completion carries `PluginIcons.EONX`. Annotator warnings use `enforcedTextAttributes` for an
  exact yellow (background = "highlight", `LINE_UNDERSCORE` = "underline").
- No remote is configured; commit locally, one feature per commit.