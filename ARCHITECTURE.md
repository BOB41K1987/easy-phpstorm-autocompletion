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

The machine has **no JDK and no Gradle** out of the box. Homebrew's `gradle` pulls JDK 26 + Gradle 9,
both too new for the IntelliJ Platform Gradle Plugin 2.x. Use **OpenJDK 17** + the pinned Gradle 8.10.2
wrapper:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"   # installed via: brew install openjdk@17
./gradlew test --no-daemon --console=plain        # headless functional tests (the regression guard)
./gradlew buildPlugin --no-daemon --console=plain  # -> build/distributions/phpstorm-eeh-translation-nav-<version>.zip
```

First `buildPlugin`/`test` downloads the PhpStorm SDK (~1.5 GB, cached after). Build target
`platformVersion=2024.3.5` (gradle.properties), since-build 241, open upper bound; bytecode is Java 17
so it loads on PhpStorm 2024.1+.

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
- **EntityExpectationContext** — walks an `assertEntity(Entity::class)->…` chain to its entity.
- **ExceptionMessageParamsContext** — pairs `setMessageParams`/`setUserMessageParams` with the
  constructor / setUserMessage message; extracts ICU `{placeholder}` names.
- **ConstraintMessageContext** — detects a Symfony constraint attribute `message`-like named arg.
- **PluginIcons.EONX** — the eonx.com favicon (`/icons/eonx.png` + `@2x`), put on every completion.

Features (each registered in plugin.xml):

| Area | Classes |
|-|-|
| Exception message keys (`exceptions.*`/`user_messages.*`) | ExceptionTranslationKeyCompletionContributor, ExceptionTranslationKeyGotoHandler |
| Foundry factory attribute keys (calls + `defaults()`) | FactoryAttributeKeyCompletionContributor, FactoryEntityResolver |
| EntityExpectation `assertEntity(...)->toBeInDb([...])` | EntityCriteriaKeyCompletionContributor (criteria keys), EntityCriteriaListValueCompletionContributor (jsonAttributes values), EntityCriteriaJsonbAnnotator (+ AddJsonAttributeQuickFix) |
| DatabaseEntityTrait helpers (`assertEntityExists`/`getEntity`/… entity = arg0) | RepositoryCriteriaKeyCompletionContributor; list values reuse EntityCriteriaListValueCompletionContributor's spec map |
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