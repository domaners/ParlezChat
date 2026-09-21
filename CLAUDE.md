# langtutor — chat-based language learning Android app

This file is project guidance for Claude Code. It describes the target
architecture for an Android-first chat-style language-learning app
(Kotlin Multiplatform shared core, Jetpack Compose UI). The app runs an
immersive conversation with the user in the language they're learning,
powered by the Claude API using a key the user supplies themselves.

Implement against this spec. Where a decision is marked **open**, use
your judgment and note the choice made in a code comment rather than
asking — everything else here should be followed as designed.

## Feature summary

- On first launch, a modal onboarding flow (not dismissable until
  complete) asks for the user's Claude API key. The key is supplied by
  the user (BYO key), validated with a cheap test call, and stored
  encrypted with an Android Keystore-backed key — never in plaintext
  (see "Config & API key security").
  Next the flow asks five onboarding questions: native language, target
  language, interests, self-rated proficiency level, and how long they've
  been studying. Store the answers as the user's profile. The user can
  later change these values from a Settings page.
- The user is then taken to a Chat window that resembles any chat app
  such as WhatsApp or Facebook Messenger.
- The bot asks the user a simple question **in the target language** to
  kick off the conversation, then waits for the user's response.
- On every subsequent turn, act as a conversational partner, 
  being symapthetic to the user's competency and experience levels for that langauge: 
  reply  **only in the target language**, on topics related to the user's
  interests, if they have an intermediate/high level, otherwise provide a 
  simplified answer with the user's native language translation underneath, italicized.
- If the user asks for an explanation of a message they have received
  from the bot, the bot explains it **in the user's native language**: a
  translation plus a short grammar and vocabulary breakdown. This does
  not count as a conversation turn (it is never part of the history sent
  back to Claude for the next chat reply).
- If the user asks to save a word to their dictionary, add it to the
  user's personal vocabulary list. Each entry has the word, its
  definition (in the native language), and an example sentence in the
  target language with a translation into the native language.

This is a standalone app. It is Android-first, but the shared module must
stay free of Android imports so iOS/desktop targets remain a realistic
later step (see "Out of scope").

## Tech choices

- Kotlin Multiplatform, Android target only for now. All non-UI code
  lives in the `shared` module's `commonMain` source set.
- UI: Jetpack Compose (Android app module). No Compose Multiplatform yet.
- Persistence: SQLDelight (`.sq` files, `.sqm` migrations). No Room.
- Networking: Ktor client (OkHttp engine on Android) with
  `kotlinx.serialization` for JSON. Call the Messages API directly
  (`POST https://api.anthropic.com/v1/messages`, headers `x-api-key`,
  `anthropic-version: 2023-06-01`); no JVM-only Anthropic SDK in
  `commonMain`.
- DI: Koin. Async: coroutines and `Flow`. Time: `kotlinx.datetime`.
- API-key encryption: Android Keystore behind an `expect`/`actual`
  `SecureKeyStore`. Do not use `androidx.security:security-crypto`
  (deprecated); use the Keystore APIs directly or Tink.
- No other third-party dependencies unless a genuine need comes up.

## Module layout

```
shared/src/commonMain/kotlin/<base package>/
  domain/model/        # data classes and enums (see Data model)
  domain/repository/   # repository interfaces
  domain/usecase/      # StartConversation, SendMessage, ExplainMessage, SaveWord, ...
  domain/prompt/       # PromptBuilder: system prompts and tool schemas
  data/local/          # SQLDelight schema, mappers, repository impls
  data/remote/         # ClaudeClient (interface), KtorClaudeClient, DTOs
  data/security/       # SecureKeyStore (expect), ApiKeyStore
  di/                  # Koin modules
shared/src/androidMain/  # SecureKeyStore actual (Keystore), SQLDelight driver
androidApp/              # Compose UI: onboarding, chat, menu, settings, dictionary
```

Every module talks to `ClaudeClient`, never to Ktor or HTTP directly —
that keeps the rest of the app mockable in tests. `ClaudeClient` is a
thin transport (`suspend fun send(request): Result<ClaudeResponse>` plus
`suspend fun validateKey(key): Result<Unit>`); `PromptBuilder` decides
what goes in the request.

## Menu screen layout

The menu screen slides onto the screen from the left (Compose
`ModalNavigationDrawer`, opened from a hamburger icon in the chat top bar
or an edge swipe). It gives the following options:

- Settings
- My dictionary
- Change language

## Data model (`domain/model`)

All timestamps are epoch milliseconds (UTC). Language codes are BCP-47
(`en`, `pl`, `es`); show human-readable names in the UI.

```kotlin
enum class ProficiencyLevel { A1, A2, B1, B2, C1, C2 }
// UI shows plain-language labels ("Complete beginner (A1)", "Can hold
// simple conversations (A2)", ...), not bare CEFR codes.

enum class StudyDuration {
    UNDER_1_MONTH, ONE_TO_3_MONTHS, THREE_TO_6_MONTHS,
    SIX_TO_12_MONTHS, ONE_TO_2_YEARS, OVER_2_YEARS
}

data class LearnerProfile(
    val id: Long,
    val nativeLanguage: String,
    val targetLanguage: String,
    val interests: List<String>,        // free text, trimmed, max 10
    val level: ProficiencyLevel,
    val studyDuration: StudyDuration,
    val isActive: Boolean,              // exactly one profile is active
    val createdAt: Long,
    val updatedAt: Long,
)

enum class Role { USER, ASSISTANT }
enum class DeliveryStatus { PENDING, SENT, FAILED }   // FAILED = show retry

data class ChatMessage(
    val id: Long,
    val profileId: Long,
    val role: Role,
    val content: String,
    val status: DeliveryStatus,
    val createdAt: Long,
)

data class GrammarNote(val theme: String, val note: String)   // note is in the native language

data class Explanation(
    val messageId: Long,                // the assistant message explained
    val translation: String,
    val grammarNotes: List<GrammarNote>,
    val createdAt: Long,
)

data class VocabEntry(
    val id: Long,
    val profileId: Long,
    val term: String,                   // as it appears in the target language
    val partOfSpeech: String?,
    val definition: String,             // native language
    val exampleTarget: String,
    val exampleNative: String,
    val sourceMessageId: Long?,         // message it was saved from, if still present
    val createdAt: Long,
)
```

Design decisions baked into this model:

- **One `LearnerProfile` per target language**, each holding all five
  onboarding answers, and one continuous chat thread per profile. This is
  what makes "Change language" work: it switches the active profile
  (or creates a new one), and the chat and dictionary are scoped to it.
- `Explanation` is a separate entity keyed by message, not a chat
  message. That is how "does not count as a turn" is enforced
  structurally, and it also caches explanations so re-opening one costs
  no API call.
- The API key is not part of the data model; it lives only in
  `ApiKeyStore`.

## Storage (`data/local`)

SQLDelight, schema version 1. Enable foreign keys
(`PRAGMA foreign_keys = ON`) when the driver is created.

```sql
CREATE TABLE learner_profile (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    native_language TEXT NOT NULL,
    target_language TEXT NOT NULL,
    interests       TEXT NOT NULL,          -- JSON array, via column adapter
    level           TEXT NOT NULL,          -- ProficiencyLevel name
    study_duration  TEXT NOT NULL,          -- StudyDuration name
    is_active       INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL,
    UNIQUE (native_language, target_language)
);

CREATE TABLE message (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id INTEGER NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    role       TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content    TEXT NOT NULL,
    status     TEXT NOT NULL DEFAULT 'SENT' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at INTEGER NOT NULL
);
CREATE INDEX message_profile_created ON message (profile_id, created_at);

CREATE TABLE explanation (
    message_id    INTEGER PRIMARY KEY REFERENCES message(id) ON DELETE CASCADE,
    translation   TEXT NOT NULL,
    grammar_notes TEXT NOT NULL,            -- JSON array of {theme, note}
    created_at    INTEGER NOT NULL
);

CREATE TABLE vocab_entry (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    profile_id        INTEGER NOT NULL REFERENCES learner_profile(id) ON DELETE CASCADE,
    term              TEXT NOT NULL COLLATE NOCASE,
    part_of_speech    TEXT,
    definition        TEXT NOT NULL,
    example_target    TEXT NOT NULL,
    example_native    TEXT NOT NULL,
    source_message_id INTEGER REFERENCES message(id) ON DELETE SET NULL,
    created_at        INTEGER NOT NULL,
    UNIQUE (profile_id, term)
);

CREATE TABLE app_setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- keys: onboarding_complete ("true"), model (override of DEFAULT_MODEL)
```

Repository rules:

- Repositories expose `Flow`s for anything the UI observes (messages for
  the active profile, vocab list, profiles). One-shot writes are
  `suspend` functions.
- Setting the active profile is a single transaction: clear all
  `is_active`, then set one.
- Saving a duplicate word (same profile, case-insensitive) is not an
  error: return the existing entry and tell the user it's already saved.
- Profile creation during onboarding is one transaction, and it happens
  only after the API key has been validated and stored.
- Schema changes ship as numbered `.sqm` migrations with SQLDelight's
  migration verification enabled. Never edit a released `.sq` schema
  without a migration.
- Chat history is app data, not a secret, but the API key must never be
  written to this database.

## Config & API key security (`data/security`)

- `SecureKeyStore` is `expect`/`actual`: `suspend fun put(alias, value)`,
  `suspend fun get(alias): String?`, `suspend fun delete(alias)`. The
  Android `actual` generates a non-exportable AES-256-GCM key in the
  Android Keystore, encrypts the API key with a fresh random IV per
  write, and stores `iv + ciphertext` (Base64) in private app storage
  (DataStore or private SharedPreferences). No custom crypto, no key
  material in code, and no plaintext fallback: if the Keystore is
  unavailable, surface an error and ask the user to re-enter the key.
- `ApiKeyStore` wraps `SecureKeyStore` with the single alias
  `claude_api_key` and is the only class that touches the key.
- Set `android:allowBackup="false"` (or exclude the key file from
  backup and device transfer rules). Keystore keys don't survive a
  restore, so a restored ciphertext would be permanently unreadable.
- The key is sent only in the `x-api-key` header over HTTPS to
  `api.anthropic.com`. Redact that header in any Ktor logging
  (`sanitizeHeader`). Never log the key, put it in exception messages,
  or include it in crash reports or analytics.
- In the UI the key is a password-style field. Settings shows it masked
  (`sk-ant-…abcd`), and the only actions are "Replace" (validate first,
  then overwrite) and "Remove" (returns the user to onboarding step 1).
- Be honest in the UI copy: the key is encrypted on this device, but a
  rooted device or a compromised phone can still expose it. Suggest the
  user create a dedicated key with a spend limit.
- Non-secret config (`onboarding_complete`, model override) lives in the
  `app_setting` table. `DEFAULT_MODEL` is a single constant in one file
  (**open**: choose a current Claude model ID when implementing and note
  why in a comment; verify against the models documentation, don't
  guess). `max_tokens` is a constant per call type (chat reply ≈ 400,
  explanation ≈ 800, word definition ≈ 300).

## Conversation loop (`domain/usecase`, `domain/prompt`, UI)

### App start and onboarding

1. If `onboarding_complete` is not set, show the onboarding modal flow,
   otherwise open the Chat screen for the active profile.
2. Onboarding step 1: API key. Validate it with `GET /v1/models?limit=1`
   (no tokens consumed). On 401/403 show "That key wasn't accepted" and
   stay on the step. On success, store it via `ApiKeyStore`.
3. Steps 2–6: native language, target language, interests, level,
   study duration. Require all five. Then create the profile, mark it
   active, and set `onboarding_complete`.
4. Native and target language must differ.

### Kick-off

When the active profile has no messages, `StartConversation` asks Claude
for an opening question. The Messages API requires the first message to
be a `user` turn, so send a hidden instruction as that turn (for
example "Begin the conversation now.") and do **not** persist it. Persist
only the assistant's reply.

### Each user turn (`SendMessage`)

1. Insert the user message with status `PENDING` and show it
   immediately. Disable send (or queue) while a reply is in flight, and
   show a typing indicator.
2. Build the request: system prompt from `PromptBuilder` plus the last
   `HISTORY_WINDOW` persisted messages (**open**, default 20). Send only
   `SENT` and the current `PENDING` message; merge consecutive
   same-role messages so roles alternate. Explanations and vocab are
   never included.
3. On success: mark the user message `SENT`, insert the assistant reply.
   On failure: mark it `FAILED` and show a tap-to-retry affordance (see
   "Error handling"). The user's text is never lost.
4. Non-streaming for the first version (**open**: streaming with a live
   typing effect is a later improvement).

### System prompt (chat replies)

`PromptBuilder` renders this from the active profile; keep it in one
place so it's easy to tune:

```
You are a friendly conversation partner helping the user practise
{target_language}. The user's native language is {native_language}.
Their level is {level} (studied for {study_duration}). Their interests
include: {interests}.

Rules:
- Reply ONLY in {target_language}, even if the user writes in
  {native_language}. Never include translations or explanations unless
  the user's message is impossible to answer otherwise.
- Keep vocabulary and grammar suited to {level}. Use short messages
  (1–3 sentences) like a text-message chat.
- Steer the conversation toward the user's interests. Ask one simple
  follow-up question at a time.
- Do not correct mistakes unless asked. Never break character.
```

### Explanation (`ExplainMessage`)

- Triggered by long-pressing an assistant bubble → "Explain". Show the
  result in a bottom sheet, not a chat bubble.
- If an `explanation` row exists for the message, show it with no API
  call. Otherwise call Claude with a dedicated system prompt ("explain in
  {native_language}; grade to {level}") and a **forced tool call** for
  structured output (`tool_choice: {type: "tool", name: "submit_explanation"}`).
  Store the result.

```json
{
  "name": "submit_explanation",
  "description": "Return a translation and short grammar breakdown of a message.",
  "input_schema": {
    "type": "object",
    "properties": {
      "translation": { "type": "string", "description": "Translation into the user's native language" },
      "grammar_notes": {
        "type": "array",
        "maxItems": 5,
        "items": {
          "type": "object",
          "properties": {
            "theme": { "type": "string", "description": "e.g. 'accusative case', 'past tense'" },
            "note":  { "type": "string", "description": "1–2 sentences in the native language, quoting the relevant words" }
          },
          "required": ["theme", "note"]
        }
      }
    },
    "required": ["translation", "grammar_notes"]
  }
}
```

### Save word (`SaveWord`)

- Triggered by tapping a word in the explanation sheet, or selecting a
  word in a bubble → "Save to dictionary". The input is the selected
  term plus the message it came from as context.
- Call Claude with a forced `submit_vocab_entry` tool; then insert a
  `vocab_entry`. Show a confirmation with the entry, and let the user
  undo.

```json
{
  "name": "submit_vocab_entry",
  "description": "Define a word from a message in the user's target language.",
  "input_schema": {
    "type": "object",
    "properties": {
      "term": { "type": "string", "description": "Dictionary form of the word if it differs from the selected form" },
      "part_of_speech": { "type": "string" },
      "definition": { "type": "string", "description": "Native-language definition, one line" },
      "example_target": { "type": "string", "description": "A new example sentence at the user's level, in the target language" },
      "example_native": { "type": "string", "description": "Translation of the example into the native language" }
    },
    "required": ["term", "definition", "example_target", "example_native"]
  }
}
```

### My dictionary

A searchable list for the active profile, newest first. Each row shows
the term, definition, and example with its translation. Swipe or menu to
delete. No export or study modes yet.

### Settings

Edit native language, interests, level and study duration, replace or
remove the API key, and (advanced) override the model. **Target language
is not editable here**: changing it is "Change language", which
keeps each language's thread and dictionary separate. Profile edits take
effect on the next request; existing messages are not rewritten.

### Change language

Lists the user's profiles with the active one marked, plus "Add
language". Adding runs a short flow (target language, level, interests,
study duration, with native language pre-filled), creates a profile,
makes it active, and returns to a fresh chat, which triggers the
kick-off. Selecting an existing profile just switches to it.

## Error handling expectations

Map every failure to a sealed `ClaudeError` in `data/remote`, and only
ever show the user a friendly, localized message (never raw exception
text or response bodies).

| Situation | Type | Behavior |
| --- | --- | --- |
| 401 / 403 | `InvalidApiKey` | No retry. Keep the message `FAILED`, show a banner with a "Fix in Settings" action. |
| 429 | `RateLimited` | Auto-retry up to 2 times, honoring `retry-after`, then show retry. |
| 5xx / 529 | `Overloaded` | Auto-retry up to 2 times (1s, 2s, plus jitter), then show retry. |
| Timeout / no network | `Network` | Same as above; if offline, say so and let the user retry later. |
| 400 | `BadRequest` | No retry. Show a generic error, and if the API error text mentions credits or billing, say "your Anthropic account has an issue" (the user pays for their own key). |
| Missing or invalid tool output | `MalformedResponse` | Retry once, then show "Couldn't generate an explanation", with nothing stored. |
| `stop_reason: max_tokens` | (not an error) | Show the reply as is. |

- Ktor timeouts: connect 10 s, request/socket 60 s.
- A failed send never drops user input. A failed explanation or save-word
  call leaves the chat unchanged and shows an inline error in the sheet.
- Never let an unhandled exception in a coroutine reach the UI; use case
  functions return `Result` and ViewModels turn failures into UI state.
- Database errors: log a redacted message, show a generic "something went
  wrong", and do not silently continue with partial writes (use
  transactions).
- Logging: no API key, no full message content in release builds.

## Testing

- Test runner: `kotlin.test`, `kotlinx-coroutines-test`, Turbine for
  `Flow`s. Shared logic is tested without a device where possible.
- **No test ever calls the real Claude API.** Domain tests use a
  `FakeClaudeClient`; `KtorClaudeClient` is tested with Ktor
  `MockEngine`.
- Repositories: test against an in-memory SQLDelight driver (JVM
  `JdbcSqliteDriver` in `androidUnitTest` until other targets exist).
  Include a migration test for each `.sqm`.
- Must-have cases:
  - History sent to Claude excludes explanations, vocab and the hidden
    kick-off turn; roles alternate; window is applied.
  - A failed send leaves the message `FAILED`, retry succeeds, and no
    duplicate is created.
  - Explanation is cached: the second request makes no client call.
  - Duplicate vocab save is idempotent (case-insensitive).
  - Switching profile scopes messages and dictionary correctly.
  - `PromptBuilder` output contains the target language, level, and
    interests (simple string assertions, not full snapshots).
  - Error mapping for each row in the table above, including
    `retry-after` handling and the retry cap.
  - The API key never appears in the database or in logged output.
- `SecureKeyStore` needs the real Keystore: cover it with an
  instrumented test (encrypt/decrypt round trip, tamper detection),
  since JVM unit tests can't exercise it.
- UI: Compose UI tests for onboarding validation and the chat retry
  affordance. Manual smoke checklist for a real key lives in
  `docs/manual-test.md`, run locally only.

## Additional instructions

  - After each update of the code, increment the minor version number of the app

## Out of scope for this skeleton

- iOS and desktop targets, and Compose Multiplatform. Keep `commonMain`
  free of Android imports so they stay possible.
- Push notifications that mimic incoming messages (**open**: whether they
  would trigger live API calls or use pre-written local prompts is
  undecided; don't build any notification scaffolding yet).
- A grammar knowledge base built from conversation content.
- Any backend, accounts, sync or cloud backup. Data stays on the device.
- Multiple parallel conversation threads per language.
- Voice input/output, flashcards or spaced-repetition study modes.
- Streaming responses (see **open** above).
- Analytics or crash-reporting SDKs (privacy, given BYO-key usage).
- Translating the app's own UI (English only for now).
- Play Store release work, billing, or usage/cost dashboards.
