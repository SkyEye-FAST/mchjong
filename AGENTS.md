# Project instructions

## Scope

Develop features on `main`. Synchronize compatibility branches only when explicitly
requested. Build profiles and the port procedure are in
[DEVELOPMENT.md](docs/DEVELOPMENT.md).

Implement the requested behavior with the smallest complete change. Reuse existing
dependencies and check their APIs before adding code or packages. Keep components
separate by responsibility. Remove obsolete paths instead of adding compatibility
layers, fallback implementations or migrations. Do not introduce abstractions for
speculative requirements or interrupt working features with unfinished replacements.

Shared gameplay and presentation belong in `common`; Minecraft-independent rules
belong in `engine`. Loaders adapt platform APIs. The server authorizes actions,
inventory changes and access to private information. Optional integrations must load only
when their dependencies are present; do not bundle third-party mod implementations.

## Documentation and language

Read only the guides relevant to the task. The README is the documentation entry
point. Development instructions, interface conventions and custom-content formats
each have one guide under `docs/`. [TERMS.md](TERMS.md) owns localization conventions.

Update documentation when its reader-facing contract changes, not merely because
code changed. Keep instructions needed to use or maintain this repository. Do not
add implementation inventories, test-coverage catalogues, acceptance archives,
project diaries or copies of dependency documentation. A resolved task does not
require a new guide. Delete obsolete documents and update their references rather
than leaving migration pages or moving the same prose elsewhere.

Write repository prose and code comments in English, with ASCII punctuation outside
quoted localized text. Organize each section around a reader's task or a maintenance
decision. When removing surrounding material, rewrite the remaining paragraph so
its purpose, references and sequence remain clear, or remove it entirely. Do not
keep isolated facts simply because they appeared in the previous document. Reduce
redundant content, not the context needed to understand the remaining instructions.

Keep complete subjects, action objects and logical connections. Do not omit head
nouns to shorten prose, headings, lists, tables, comments, UI labels or tooltips,
even when context seems clear or space is limited. Use complete names and units,
with only the locale-specific abbreviations allowed in
[TERMS.md](TERMS.md#names-and-units). A control too narrow for its full label may
use a compact label only under the conditions in
[TERMS.md](TERMS.md#compact-labels); a compact label never names a variant, preset
or scoring pattern. English may use MCR; Chinese must retain
`国标麻将`/`國標麻將`. Taiwanese mahjong must not become Taiwanese or Taiwan, because
the place and the game are different things. Preserve code identifiers and literal
quotations exactly without treating their shortened forms as models for new text.

Preserve license notices and release history. Use dates to identify releases and
source editions, not routine reviews.

Update all four language files together, with identical keys and compatible format
arguments, two-space indentation, LF and alphabetical key order. Read the callers
before changing a term. Keep agreed localized text intact when integrating it;
integration is not permission to summarize or rewrite it.

## Code and resources

Follow the existing Java and Kotlin style. Javadoc and KDoc explain responsibilities
and caller contracts; other comments explain reasons or non-obvious constraints.
Check actual callers before documenting mutation, errors, state, threads or units.
Do not narrate code or add empty documentation templates.

Use the shared widgets, palette and render/picking geometry described in
[UI_STYLE.md](docs/UI_STYLE.md). Edit asset generators and their source inputs,
then refresh affected generated assets. Resource locations and pack formats are
in [ASSETS.md](docs/ASSETS.md). Dependency versions belong in `gradle.properties`;
do not duplicate them in guides unless they identify a user-facing requirement.

## Validation

Complete the requested product change without expanding the test system.
A change does not require new tests merely because it changes code.

Add a permanent test only when it protects a specific, meaningful behavior
against a credible regression that existing tests do not already cover.
Prefer a small regression case in an existing suite. One-off validation
does not need to become a committed script, fixture, or snapshot.

New test infrastructure requires an explicitly scoped task. Do not add or
expand test runners, shared harnesses, source sets, dependencies, screenshot
systems, or CI matrices as incidental work during a feature or bug fix.
Do not add production abstractions solely to support a test harness.

Run the smallest existing checks needed for the changed behavior. Do not
expand validation across unrelated features, loaders, locales, viewports,
or complete matches. Use real-client checks when the changed behavior
requires them, not as a routine prerequisite for every delivery.

Remove obsolete and redundant tests together with their exclusive helpers,
dependencies, tasks, and documentation requirements. Do not preserve them
as disabled suites or replacement frameworks. Do not delete or weaken a
valid test to conceal an unresolved product defect.

Do not repair unrelated test infrastructure as part of the current task.
Report the blocker and the validation actually completed. A delivery with
no new tests is acceptable; unsupported claims of successful validation
are not.

Commands are in [DEVELOPMENT.md](docs/DEVELOPMENT.md#verification).
Documentation-only changes need a diff and link review, not a client run or full
build. Keep temporary checks, logs and screenshots outside tracked source.

## Delivery

Meaningful product changes get one entry under `CHANGELOG.md`'s Unreleased section.
Documentation cleanup and routine checks do not. Ordinary work does not change
`mod_version`; keep its `-SNAPSHOT` suffix. Follow the development guide for an
explicit release or port synchronization.

Use Conventional Commits and the existing GPG signing setup. Check
`git signing-agent status`, commit complete batches with `git commit -S`, and verify
the signature before pushing. Preserve unrelated working changes and do not push
unrelated pending commits as part of a delivery.
