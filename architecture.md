# Expense Tracker App — Base Architecture Plan (Android)

## 1. Purpose of This Document

This is a **foundational architecture plan**, not a feature spec. It defines
the technical structure, layering, and conventions the app should be built on
— module boundaries, data flow, storage, networking, and extension points.

Features (voice capture, widgets, AI extraction, whatever comes next) will be
specified separately, one at a time, as their own asks. This document should
make the codebase ready to receive those features cleanly, without assuming
or hard-coding any single one of them as "the" flow. Do not treat any
specific feature as the core loop — the architecture should support many
entry points (manual entry, voice, widget, import, etc.) as peers, all
funneling into the same data layer.

**Platform:** Android only (Kotlin + Jetpack Compose). iOS is explicitly out
of scope for now; do not add cross-platform abstractions for a hypothetical
future port. Keep business logic (data model, repository, API client)
decoupled from Android UI code as good practice, but do not over-engineer for
portability that isn't planned yet.

---

## 3. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose | Widget UI uses Glance (Compose for widgets) |
| Local storage | Room (SQLite) | Source of truth for all expense data |
| Speech-to-text | Android `SpeechRecognizer` | On-device where supported; free |
| NLP extraction | LLM via backend proxy | See §5 — do not call LLM directly from app |
| Background work | WorkManager | For sync, reminders, retry queue |
| Networking | Retrofit + OkHttp | Talks only to your own backend, not LLM providers directly |
| DI | Hilt | Standard for this scale of app |
| Min SDK | API 26+ (Android 8.0) | Adjust based on target audience; widgets/Glance need reasonably modern API |

---

## 4. App Module Structure

The `feature/` layer is intentionally left open — it should be easy to drop
in a new feature module without touching `core/`. `core/` should never import
from `feature/`; dependencies flow one direction only.

```
app/
├── core/
│   ├── data/
│   │   ├── local/           # Room DB, DAOs, entities
│   │   ├── remote/          # Retrofit API client, DTOs
│   │   └── repository/      # ExpenseRepository — single source of truth,
│   │                        # entry-point-agnostic (doesn't care if data
│   │                        # came from a form, voice, import, etc.)
│   ├── model/                # Domain models (Expense, Category, etc.)
│   └── util/                  # Formatters, date utils, result wrappers
├── feature/
│   └── expense_core/          # Main app screens: entry form, list, history
│                               # (additional feature modules added later,
│                               # each as its own peer under feature/)
├── di/                        # Hilt modules
└── MainActivity / App.kt
```

Any new capability (a new input method, a new integration, a new screen)
should be addable as a new folder under `feature/`, wired into `core/`
through the repository layer — not by branching logic inside existing
features.

---

## 5. Backend Proxy Layer (For Any Future AI/Network Features)

Any feature that needs to call an external AI/LLM API should go through a
**thin backend proxy**, not call the provider directly from the app.
Reasons, as a general architectural principle:

- Embedded API keys can be extracted from the APK.
- A shared quota control point matters when free tiers are per-project, not
  per-user.
- You'll want to switch providers/models/keys without shipping an app update.

General shape (to be filled in once a specific AI feature is specced):

```
Android App --(request)--> Backend Proxy --(provider-specific call)--> LLM API
Android App <--(clean structured response)-- Backend Proxy <--(raw response)--
```

- Keep the proxy stateless; the app owns persistence via Room.
- Design the proxy's provider integration behind an interface/adapter so
  swapping providers or models later is a config change, not a rewrite.
- Own rate limiting, retries with backoff, and logging at this layer, not in
  the app.

This section stays generic until a concrete AI feature is specced — at that
point it gets its own request/response contract.

---

## 6. Data Model (Room) — Baseline

This is the minimum baseline shape. Extend it per-feature as needed rather
than redesigning it; new input methods should populate the same table
through the same repository.

```kotlin
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val currency: String,
    val category: String,
    val merchant: String?,
    val description: String,
    val source: String,               // how this entry was created, e.g. "manual"
    val timestamp: Long
)
```

`source` exists from the start so that however entries get created later
(manual form, or any future input method), it's tracked without a schema
migration.

---

## 7. Permissions Policy

- Only request permissions a shipped feature actually needs, when it needs
  them — don't pre-declare permissions for features that aren't built yet.
- Keep the overall permission footprint minimal; this matters for both Play
  Store review and user trust.

---

## 8. Build Order Principle

Build `core/` (data layer, repository, basic manual-entry UI) first and get
it fully working end to end before adding any additional feature module.
Every feature after that should be additive on top of a working base, not a
prerequisite for having *something* working.

Specific features and their build order will be specified separately as they
come up.

---

## 9. Explicitly Out of Scope For Now

- iOS / cross-platform abstraction
- Any specific feature not yet specced (voice, widgets, AI extraction, sync,
  budgeting, etc.) — this document defines the base the features will sit on,
  not the features themselves
