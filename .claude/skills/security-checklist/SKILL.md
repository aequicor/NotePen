---
name: "security-checklist"
description: "Eight red-flag patterns to scan for before approving any step that touches input parsing, authentication, secrets, SQL, or network boundaries."
---
<skill name="security-checklist">

<purpose>
Eight red-flag patterns to scan for before approving any step that touches input parsing, authentication, secrets, SQL, or network boundaries.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 1 Stage 2 | drafting a step that touches an input or auth boundary — mark the step `standard` or `heavy` |
| Session 2 Stage 3 step 5 | reviewing the diff of a step whose Goal mentions auth, input, SQL, secrets, or network |
| Session 3 Stage 2 | drafting a fix for a defect reported as a security issue |

Skip when: the diff is pure styling, doc, or test-only. A diff that adds a new dependency from a registry always invokes this skill regardless of tier.
</when_to_invoke>


<procedure>
Eight patterns. Skim the diff against each — any hit needs an entry in the STEP SUMMARY's Uncertain or Plan-deviations section, not silent acceptance.

## 1. Input crossing a trust boundary without validation

A new parameter, query string, header, or message field reaches business logic without a checked shape (type, length, allowed values). Reject at the boundary, not three layers in.

## 2. String concatenation into a SQL / shell / HTML / path expression

`"SELECT … " + name`, `exec("cmd " + arg)`, `Runtime.exec(s)`, path joins via `+` are the canonical injection shapes across SQL, OS command, XSS, and path-traversal. Use parameterized queries, the language's exec-with-argv form, an HTML-encoding template, and the platform's path-join helper.

## 3. Secret hard-coded in source or test

Strings matching `AKIA…`, `sk-…`, `Bearer …`, JWT shapes, RSA/EC private-key blocks, GitHub `ghp_…` / `gho_…`, Slack `xoxb-…`, or any literal that looks like a credential. The kit's `secrets_policy.deny_patterns` is the floor — not the ceiling.

## 4. Authentication or authorization shortcut

`if (env === 'dev') skipAuth = true`, a feature flag that bypasses the auth middleware, a debug endpoint that returns user data without a session check. These are how prod incidents start. Each one must have an explicit Plan-deviations line.

## 5. Cryptography below the language's defaults

`MD5`, `SHA-1` for password hashing, `Math.random()` / `rand()` for tokens, ECB block mode, custom KDF, a hand-rolled HMAC. Use the platform-native primitive (`crypto/rand`, `bcrypt`/`argon2`, `AES-GCM`, `crypto.subtle.sign`).

## 6. Deserialization of attacker-controlled data into a polymorphic shape

`pickle.loads(req)`, `ObjectInputStream.readObject(req)`, Jackson `@JsonTypeInfo` on user input, YAML `!!python/object`. Restrict to a closed type set or a JSON-only path.

## 7. Server-side request to a URL the caller supplied

A new `fetch(url)`, `http.Get(url)`, or `WebClient.get(url)` where `url` came from the request. SSRF. Allow-list the host before fetching; refuse private IP ranges (`127.0.0.0/8`, `10.0.0.0/8`, `169.254.0.0/16`, `metadata.google.internal`).

## 8. Logging that leaks the body it just validated

`log.info("login req: $req")`, `console.log(user)` printing the full object after auth — passwords, tokens, PII end up in log aggregation. Log id + outcome, not body.

## Anti-patterns this list prevents

- "Sanitize" with a custom regex — every custom sanitizer is wrong eventually; lean on the platform's encoder.
- "Temporary" auth bypass for testing committed to main — there is no temporary in git.
- Dependency added with a CVE older than 12 months — check the lockfile diff against the registry's advisory feed.
</procedure>

<output_format>
Any hit fires a one-line entry in the STEP SUMMARY's Uncertain section: `Security: pattern #<n> — <one-line pointer to file:line>`. The skill produces no separate artifact; it shapes the human-required side of the next summary.
</output_format>

</skill>
