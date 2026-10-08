# Security

## Reporting a problem

Please don't open a public issue for a security problem.

- **Preferred:** [open a private advisory](https://github.com/lastdaytheOG/Shiny/security/advisories/new) on GitHub. Only the maintainer can see it.
- **Or email:** [security@shinymusic.in](mailto:security@shinymusic.in)

A useful report says what is affected (the app, the Listen Together and Social servers in `server/`, or the website), how to reproduce it, and what someone could do with it. A proof of concept helps; a fix is welcome but not expected.

You'll get a reply once the report has been read, and credit in the release notes when the fix ships, unless you'd rather stay unnamed. Please give the fix time to reach users before publishing details.

## What is covered

| | |
| :-- | :-- |
| **Supported** | The latest release, and `main` |
| **In scope** | The Android app, the code in `server/`, shinymusic.in |
| **Out of scope** | YouTube, Spotify, Discord and other services Shiny talks to; report those to their owners. Modified or re-signed builds of Shiny |

## Checking that an APK is genuine

Official releases are signed with one key. Its SHA-256 fingerprint is:

```
F3:70:1C:9C:1C:87:54:F4:79:A9:D2:20:D6:E6:56:B1:12:1C:CA:43:B6:1C:7B:BE:FD:15:D8:32:A8:2B:19:46
```

Check a download with `apksigner verify --print-certs Shiny-*.apk`. An APK with any other fingerprint was not built by this project.

## If you contribute

Keystores, passwords, `local.properties` and `google-services.json` stay out of the repository; `.gitignore` already covers them. If a secret is committed by mistake, say so straight away so it can be revoked. Deleting the commit is not enough.
