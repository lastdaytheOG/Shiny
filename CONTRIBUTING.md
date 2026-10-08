# Contributing to Shiny

Thanks for wanting to help. Shiny gets better through bug reports, translations and pull requests alike, and you don't need to write code to be useful.

## Ways to help

- **Report a bug.** Use the [bug form](https://github.com/lastdaytheOG/Shiny/issues/new/choose). The steps to reproduce matter most.
- **Suggest an idea.** Use the idea form, and start from what you were trying to do.
- **Fix something.** Small, focused pull requests are reviewed fastest.
- **Report a security problem.** Privately, as described in [SECURITY.md](SECURITY.md).

For anything larger than a fix, open an issue first. Shiny has a deliberate design, and it's a shame to build something that then doesn't fit.

## Build it

You need JDK 21 and the Android SDK. [SETUP.md](SETUP.md) has the full walkthrough; the short version is:

```bash
git clone https://github.com/lastdaytheOG/Shiny.git
cd Shiny
cp local.properties.template local.properties   # then set sdk.dir
./gradlew assembleUniversalFossDebug
```

The `foss` build needs no Firebase file or signing key, so it is the one to develop against.

## Before you write code

Three documents describe how Shiny is put together. Read the one that matches your change:

| If you're changing | Read |
| :-- | :-- |
| Anything on screen | [DESIGN.md](DESIGN.md): the Liquid design system, its components and its rules |
| Project structure, modules, tests | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Code taken from another project, or a dependency | [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md) |

A few things reviewers will always check:

- **The interface uses the Liquid components** in `ui/liquid`. New screens don't bring their own buttons, sheets or colours.
- **Nothing gets slower.** Scrolling, start-up and the player are measured. If a change touches them, say how you checked.
- **Every string is a resource.** No user-facing text in Kotlin.
- **Database changes come with a migration** and the updated schema file.
- **Existing copyright notices are left alone.** If a file carries another author's notice, it stays exactly as written.

## Using other people's code

Shiny is GPL-3.0. Code you bring in must have a compatible licence, and must be credited in the same pull request:

1. Add the project to the table in the README's Acknowledgements.
2. Add it to `licenses/bundled.json`, and put its licence file in `licenses/notices/` if it isn't GPL.
3. After adding or updating a dependency, run `python scripts/third_party_licenses.py` and commit the result.

Say plainly in the pull request what was copied and from where.

## Sending a pull request

1. Branch from `main`.
2. Keep it to one change. Unrelated clean-ups belong in their own pull request.
3. Run the tests for the area you touched:
   ```bash
   ./gradlew :app:testUniversalFossDebugUnitTest
   ./gradlew :lyrics:testDebugUnitTest :canvas:test :innertube:test
   ```
4. Write the commit summary as one plain sentence saying what the change does, such as `Fix lyrics drifting after a seek`.
5. In the description, say what changed, why, and how you tested it. Add a screenshot or a short recording for anything visual, and link the issue it closes.

By sending a pull request you agree that your contribution is licensed under the GPL-3.0, like the rest of Shiny.

## Conduct

Be kind. The details are in the [Code of Conduct](CODE_OF_CONDUCT.md).
