package com.pocketwise.core.data.voice

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.appCheck
import com.pocketwise.BuildConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One expense the model found in a voice note, after app-side validation. */
data class ParsedExpense(val amount: Double, val description: String, val category: String, val date: LocalDate)

/** Model output exactly as returned — never saved without going through [sanitize]. */
data class RawExpense(val amount: Double?, val description: String?, val category: String?, val date: String?)

/** What Gemini heard, in the speaker's own language and script, plus the expenses it found. */
data class VoiceParseResult(val transcript: String, val expenses: List<ParsedExpense>)

/** Carries a message that's safe to show the user as-is. */
class VoiceParseException(message: String) : Exception(message)

const val FALLBACK_CATEGORY = "Other"

private const val MODEL = "gemini-3.5-flash-lite"

private val SystemPrompt = """
    You turn a short voice note into expense records for a personal expense tracker.
    The audio can be in any language (English, Nepali, Hindi, or any other), or several mixed in one sentence.
    Rules:
    - transcript: what was said, in the speaker's own language and script. Empty if nothing intelligible was said.
    - One record per distinct purchase mentioned. If no amount is stated, return an empty "expenses" array.
    - Never invent a purchase or an amount.
    - amount: a positive number in the user's currency. Ignore currency words (rupees, rupaiya, रुपैयाँ, Rs, dollars).
      Understand spoken numbers in any language, e.g. "two hundred fifty" / "dui saya pachas" / "दुई सय पचास" = 250,
      "hajar" / "हजार" = 1000, "dedh saya" = 150, "sadhe tin saya" = 350, "lakh" / "लाख" = 100000.
    - description: a short title-case label in Latin script, using the speaker's own word for the item.
      Romanize non-Latin words, do not translate them: "सब्जी" -> "Sabji", "चिया" -> "Chiya"; "coffee" -> "Coffee".
    - category: the closest match by meaning from the allowed categories, whatever the language
      (e.g. sabji/vegetables -> groceries, chiya/khana/coffee/lunch -> food, bus/taxi/pathao/uber -> transport);
      use "Other" if nothing fits.
    - date: ISO yyyy-MM-dd. Resolve relative words in any language ("yesterday", "hijo" / "हिजो",
      "asti" / "अस्ति" = day before yesterday, "last Friday") against today's date. Default to today. Never a future date.
""".trimIndent()

@Singleton
class VoiceExpenseParser @Inject constructor() {

    /**
     * Starts fetching the App Check token while the user is still speaking, so the
     * Gemini call that follows doesn't have to wait for it. The SDK caches the
     * token; this is fire-and-forget.
     */
    fun warmUp() {
        // No FirebaseApp (config missing) throws here; parse() reports that case properly.
        runCatching { Firebase.appCheck.getAppCheckToken(false) }
    }

    /** Gemini listens to the audio itself, so any spoken language works. */
    suspend fun parse(
        audio: ByteArray,
        mimeType: String,
        categories: List<String>,
        currency: String,
        today: LocalDate
    ): VoiceParseResult {
        val allowed = (categories + FALLBACK_CATEGORY).distinct()
        // Structured output: the schema (with the user's own categories as an
        // enum) constrains the model, instead of asking nicely for JSON.
        val schema = Schema.obj(
            mapOf(
                "transcript" to Schema.string(),
                "expenses" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "amount" to Schema.double(),
                            "description" to Schema.string(),
                            "category" to Schema.enumeration(allowed),
                            "date" to Schema.string(),
                        )
                    )
                )
            )
        )
        val requestContext = """
            Today: $today (${ZoneId.systemDefault().id})
            Currency: $currency
            Allowed categories: ${allowed.joinToString()}
            The attached audio is the user's voice note.
        """.trimIndent()

        val startedAt = System.nanoTime()
        val json = try {
            Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = schema
                        temperature = 0f
                    },
                    systemInstruction = content { text(SystemPrompt) }
                )
                .generateContent(
                    content {
                        inlineData(audio, mimeType)
                        text(requestContext)
                    }
                )
                .also { logUsage(it, elapsedMs = (System.nanoTime() - startedAt) / 1_000_000) }
                .text
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // No default FirebaseApp — google-services.json isn't in the build yet.
            logFailure(e)
            throw VoiceParseException("Voice logging isn't set up yet.")
        } catch (e: QuotaExceededException) {
            throw VoiceParseException("Voice limit reached for today — add it manually.")
        } catch (e: Exception) {
            logFailure(e)
            throw VoiceParseException("Couldn't reach the AI service. Check your connection and try again.")
        }

        val (transcript, raw) = try {
            parseRaw(json ?: "")
        } catch (e: JSONException) {
            throw VoiceParseException("Couldn't understand that. Try again or add it manually.")
        }
        return VoiceParseResult(transcript, sanitize(raw, allowed, today))
    }
}

// Debug builds only, and only the error — voice notes are financial data and
// are never logged. Without this, every failure looks identical to the user.
private fun logFailure(e: Exception) {
    if (BuildConfig.DEBUG) Log.w("VoiceExpenseParser", "AI request failed: ${e::class.simpleName}: ${e.message}")
}

// Debug builds only: which model actually served the call and its token cost
// (audio counts ~32 tokens/second; thinking tokens count too). Never includes content.
private fun logUsage(response: GenerateContentResponse, elapsedMs: Long) {
    if (!BuildConfig.DEBUG) return
    val usage = response.usageMetadata
    Log.d(
        "VoiceExpenseParser",
        "served by ${response.modelVersion} in ${elapsedMs}ms (incl. App Check token); tokens in=${usage?.promptTokenCount} " +
            "out=${usage?.candidatesTokenCount} thinking=${usage?.thoughtsTokenCount} total=${usage?.totalTokenCount}"
    )
}

private fun parseRaw(json: String): Pair<String, List<RawExpense>> {
    val root = JSONObject(json)
    val transcript = root.optString("transcript").trim()
    val expenses = root.optJSONArray("expenses") ?: return transcript to emptyList()
    return transcript to (0 until expenses.length()).mapNotNull { index ->
        expenses.optJSONObject(index)?.let {
            RawExpense(
                amount = it.optDouble("amount").takeUnless { amount -> amount.isNaN() },
                description = it.optString("description"),
                category = it.optString("category"),
                date = it.optString("date"),
            )
        }
    }
}

/**
 * App-side trust boundary: the model's output is never saved as-is, even with
 * a schema. Drops anything without a real positive amount and repairs the rest.
 */
internal fun sanitize(raw: List<RawExpense>, categories: List<String>, today: LocalDate): List<ParsedExpense> =
    raw.mapNotNull { expense ->
        val amount = expense.amount?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
        val category = expense.category?.takeIf { it in categories } ?: FALLBACK_CATEGORY
        val date = expense.date
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeUnless { it.isAfter(today) }
            ?: today
        val description = expense.description?.trim()?.takeUnless { it.isEmpty() } ?: category
        ParsedExpense(amount, description, category, date)
    }
