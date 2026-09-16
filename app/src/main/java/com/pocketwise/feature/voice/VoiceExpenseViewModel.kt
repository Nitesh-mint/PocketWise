package com.pocketwise.feature.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.CategoryRepository
import com.pocketwise.core.data.repository.ExpenseRepository
import com.pocketwise.core.data.voice.ParsedExpense
import com.pocketwise.core.data.voice.VoiceExpenseParser
import com.pocketwise.core.data.voice.VoiceParseException
import com.pocketwise.core.data.voice.VoiceRecorder
import com.pocketwise.core.model.Category
import com.pocketwise.core.model.Expense
import com.pocketwise.core.util.VoiceChime
import com.pocketwise.core.util.vibrateClick
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class VoiceExpenseViewModel @Inject constructor(
    private val parser: VoiceExpenseParser,
    private val recorder: VoiceRecorder,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Listening(val level: Float = 0f, val elapsedMs: Long = 0L) : UiState
        data object Parsing : UiState
        data class Review(val transcript: String, val items: List<ParsedExpense>) : UiState
        data class Error(val transcript: String?, val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    private var voiceJob: Job? = null
    @Volatile private var stopRequested = false

    /** Record → Gemini (listens to the audio itself) → review sheet. Mic permission must already be granted. */
    fun startListening() {
        voiceJob?.cancel()
        stopRequested = false
        _state.value = UiState.Listening()
        context.vibrateClick()
        viewModelScope.launch { VoiceChime.playStart() }
        voiceJob = viewModelScope.launch {
            val audio = try {
                recorder.record(
                    shouldStop = { stopRequested },
                    // Only while still listening — a dismissed sheet must not pop back up.
                    onProgress = { level, elapsedMs ->
                        _state.update { if (it is UiState.Listening) UiState.Listening(level, elapsedMs) else it }
                    }
                )
            } catch (e: VoiceParseException) {
                _state.value = UiState.Error(null, e.message ?: "Couldn't use the microphone.")
                return@launch
            }
            if (audio == null) {
                _state.value = UiState.Error(null, "Didn't hear anything. Try again, a little closer to the mic.")
                return@launch
            }
            context.vibrateClick()
            viewModelScope.launch { VoiceChime.playStop() }

            _state.value = UiState.Parsing
            _state.value = try {
                val result = parser.parse(
                    audio = audio,
                    mimeType = VoiceRecorder.MIME_TYPE,
                    categories = categoryRepository.categories.first().map { it.name },
                    currency = userPreferences.currencyCode.first(),
                    today = LocalDate.now()
                )
                if (result.expenses.isEmpty()) {
                    UiState.Error(result.transcript, "Couldn't find an amount in that. Try again or add it manually.")
                } else {
                    UiState.Review(result.transcript, result.expenses)
                }
            } catch (e: VoiceParseException) {
                UiState.Error(null, e.message ?: "Something went wrong.")
            }
        }
    }

    /** "Done" — finish recording now and send what was said. */
    fun stopListening() {
        stopRequested = true
    }

    /** App left the screen mid-recording: discard rather than keep the mic open. */
    fun stopListeningIfActive() {
        if (_state.value is UiState.Listening) dismiss()
    }

    fun onSpeechError(message: String) {
        _state.value = UiState.Error(null, message)
    }

    /** Called when the mic is tapped, so auth is ready by the time the recording ends. */
    fun warmUp() = parser.warmUp()

    /** Closing the sheet discards everything — nothing was saved yet. */
    fun dismiss() {
        voiceJob?.cancel()
        _state.value = UiState.Idle
    }

    fun save(items: List<ParsedExpense>) {
        _state.value = UiState.Idle
        viewModelScope.launch {
            val currency = userPreferences.currencyCode.first()
            items.forEach {
                expenseRepository.addExpense(
                    Expense(
                        amount = it.amount,
                        currency = currency,
                        category = it.category,
                        description = it.description,
                        source = "voice",
                        // Noon, same convention as the manual form, so a date never
                        // slips across midnight in another time zone.
                        timestamp = it.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                )
            }
        }
    }
}
