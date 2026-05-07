---
genre: reference
title: Spec Template
topic: planning
triggers:
  - "spec"
  - "api contract"
  - "data model"
confidence: high
source: human
updated: 2026-05-07T00:00:00Z
---

# Spec: [Feature Name]

**Module:** [module-name]
**Status:** Draft | Review | Approved
**Date:** DD.MM.YYYY
**Author:** @Main / @CodeWriter
**Requirements:** [[concepts/<module>/requirements/<feature>]]

---

## Overview

High-level description of what will be built and why.

---

## Data Models

### [ModelName]

```
FieldName   Type        Required  Description
---------   ----        --------  -----------
id          UUID        yes       Primary key
name        String      yes       Display name
createdAt   Timestamp   yes       ISO-8601
```

---

## API Contracts

### [POST /api/v1/resource]

**Request:**
```json
{
  "field": "value"
}
```

**Response 200:**
```json
{
  "id": "uuid",
  "field": "value"
}
```

**Errors:**
| Code | Condition |
|------|-----------|
| 400 | Invalid input |
| 401 | Not authenticated |
| 404 | Resource not found |

---

## Business Logic

Step-by-step description of the main flow:

1. ...
2. ...
3. ...

### Edge Cases

| Case | Behavior |
|------|----------|
| [edge case] | [expected behavior] |

---

## Error Handling

| Error scenario | How handled | User-visible message |
|----------------|-------------|---------------------|
| DB unavailable | Return 503 + retry hint | "Service temporarily unavailable" |

---

## Security Considerations

- Authentication required: yes/no
- Authorization rules: [who can access what]
- Sensitive data handling: [PII, tokens, etc.]

---

## Dependencies

- External services / APIs used
- Internal modules / libraries required
- Infrastructure requirements

---

## Implementation Notes

Technical notes, constraints, or gotchas relevant to implementation.
