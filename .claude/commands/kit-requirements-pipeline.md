---
description: Run the full AI-driven requirements pipeline for a new feature. Chains BA → CornerCaseReviewer → QA → CoverageChecker → SA → CornerCaseReviewer (tech) → ConsistencyChecker. Only PO sign-off required at the end. Argument: $FEATURE_DESCRIPTION
---

You are a Senior business analyst orchestrator. Your task is to run the full AI-driven requirements pipeline for a new feature and produce a complete, reviewed requirements package.

Feature description: $FEATURE_DESCRIPTION

Hand off to `@Main` with the following prompt:

```
Run the requirements-pipeline skill for this feature.

Feature description: $FEATURE_DESCRIPTION
```

`@Main` will execute the full automated pipeline via the `requirements-pipeline` skill:

1. **INTAKE** — parse feature name and module from description.
2. **BA DRAFT** — @BusinessAnalyst generates requirements document.
3. **CC BUSINESS** — @CornerCaseReviewer attacks requirements (up to 3 iterations with @BusinessAnalyst).
4. **QA DRAFT** — @QA (Phase=REQUIREMENTS) creates the living `<feature>-test-cases.md` from requirements + corner cases.
5. **COVERAGE** — @CoverageChecker verifies all requirements and corner cases have test cases.
6. **SA DRAFT** — @SystemAnalyst generates technical spec from requirements + test cases.
7. **CC TECHNICAL** — @CornerCaseReviewer attacks tech spec (up to 3 iterations with @SystemAnalyst).
8. **CONSISTENCY** — @ConsistencyChecker verifies spec does not contradict requirements.
9. **SIGN-OFF** — presents complete artifact package to PO for `/kit-approve`.

After `/kit-approve`, use `/kit-new-feature` to hand the artifacts to `@Main` for implementation planning.

**Do not call pipeline agents directly — only @Main.**

**Output format:** After handoff, output ONLY the intake confirmation (feature name, module) and the first pipeline step status. No introductory text, no preamble.
