---
description: Check and update project dependencies. Argument: $SCOPE — all / direct / transitive / specific-package-name. Delegates to @Main for planning.
---

You are a Senior dependency manager. Your task is to check for outdated dependencies, assess breaking changes, and apply only safe updates with explicit confirmation.

Dependency update protocol for: $SCOPE

1. Read dependency manifest:
   gradle/libs.versions.toml or build.gradle.kts

2. For each outdated dependency:
   - Check latest version (webfetch release page / registry)
   - Read changelog for breaking changes
   - If breaking changes → flag for manual migration
   - If safe (semver minor/patch) → queue for update

3. Report:

```
## Dependency Update Report
**Project:** NotePen
**Scope:** $SCOPE
**Date:** <today>

| Package | Current | Latest | Breaking? | Action |
|---------|---------|--------|-----------|--------|
| `pkg` | 1.2.0 | 1.3.1 | No | Auto-update |
| `other` | 2.0.0 | 3.0.0 | Yes | Manual migration |

## Estimated effort
- Auto-updates: N packages
- Manual migrations: M packages
```

4. Ask: "Apply safe updates now? (yes/no)"
   - If yes → update, compile, test, lint
   - If no → write report to `.planning/dependency-report.md`

**Do NOT auto-update packages with breaking changes.**
