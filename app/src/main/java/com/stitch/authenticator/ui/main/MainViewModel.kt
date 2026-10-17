package com.stitch.authenticator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.crypto.TotpEngine
import com.stitch.authenticator.data.Token
import com.stitch.authenticator.data.TokenRepository
import com.stitch.authenticator.data.code
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One account as the list needs it: the code valid for *this* second plus whatever the card's
 * countdown ring draws.
 *
 * It is a snapshot, not a live object. The ticker rebuilds it once per second, so the UI never reads
 * a clock itself and two cards can never disagree about how much time is left.
 */
data class TokenUiModel(
  val id: String,
  val title: String,
  val subtitle: String,
  val code: String,
  val secondsRemaining: Int,
  val progress: Float,
  val isUsable: Boolean,
)

sealed interface MainUiState {
  /** Storage has not answered yet. Showing "no accounts yet" here would be a lie. */
  data object Loading : MainUiState

  /** Tokens in display order. An empty list is the empty state. */
  data class Ready(val tokens: List<TokenUiModel>) : MainUiState

  data class Error(val throwable: Throwable) : MainUiState
}

/** How far the one and only read of storage has got. */
private sealed interface LoadStatus {
  data object Pending : LoadStatus

  data object Loaded : LoadStatus

  data class Failed(val throwable: Throwable) : LoadStatus
}

/**
 * State holder for the token list.
 *
 * A single ticker drives every card. Per-card timers would multiply the work by the number of
 * accounts and — worse — drift apart, so two cards could show a different "5" at the same instant.
 * Because the ticker sits inside the [stateIn] chain it also stops while nothing observes the screen:
 * no codes are computed in the background, and the app burns no battery while it is off screen.
 *
 * The repository publishes an *empty* list until [TokenRepository.refresh] lands, so [LoadStatus]
 * distinguishes "nothing stored" from "not read yet". Without it, a user with accounts would see the
 * empty state flash on every cold start.
 *
 * [engine] and [ticker] are injectable so tests can drive time instead of waiting for it.
 */
class MainViewModel(
  private val repository: TokenRepository,
  private val engine: TotpEngine = TotpEngine(),
  private val ticker: Flow<Long> = tickerFlow(engine),
) : ViewModel() {

  private val loadStatus = MutableStateFlow<LoadStatus>(LoadStatus.Pending)

  val uiState: StateFlow<MainUiState> =
    combine(repository.tokens, ticker, loadStatus) { tokens, nowMillis, status ->
        when (status) {
          LoadStatus.Pending -> MainUiState.Loading
          is LoadStatus.Failed -> MainUiState.Error(status.throwable)
          LoadStatus.Loaded ->
            MainUiState.Ready(tokens.map { it.toUiModel(Instant.ofEpochMilli(nowMillis)) })
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = MainUiState.Loading,
      )

  init {
    refresh()
  }

  /** Reads storage again; also the action behind the error state's retry button. */
  fun refresh() {
    viewModelScope.launch {
      loadStatus.value = LoadStatus.Pending
      runCatching { repository.refresh() }
        .onSuccess { loadStatus.value = LoadStatus.Loaded }
        .onFailure { loadStatus.value = LoadStatus.Failed(it) }
    }
  }

  /**
   * Removes an account.
   *
   * The row leaves when the repository confirms the write, never optimistically: the list is a
   * projection of storage, so a card can never be gone from the screen while still in the file.
   *
   * A failed write is reported as an error rather than swallowed — if storage refused the change, the
   * cached list may no longer match the file, and retrying is the honest recovery.
   */
  fun delete(id: String) {
    viewModelScope.launch {
      runCatching { repository.delete(id) }
        .onFailure { loadStatus.value = LoadStatus.Failed(it) }
    }
  }

  private fun Token.toUiModel(now: Instant): TokenUiModel {
    // A token whose secret cannot be decoded must not take the whole list down with it; the card
    // shows itself as unusable instead. `isValid` also guarantees a positive period.
    val usable = isValid
    val codeValue = if (usable) code(engine, now) else ""

    return TokenUiModel(
      id = id,
      title = title,
      subtitle = subtitle,
      code = codeValue,
      secondsRemaining = if (usable) engine.secondsRemaining(period, now) else 0,
      progress = if (usable) engine.progress(period, now) else 0f,
      isUsable = usable,
    )
  }

  companion object {
    /** Keep ticking briefly after the screen leaves, so a configuration change does not restart it. */
    private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

    private const val SECOND_MILLIS = 1_000L

    /**
     * Emits the epoch milliseconds of every second boundary, forever.
     *
     * Aligned to the boundary rather than "every 1000 ms from now": codes roll over *on* the boundary,
     * so this keeps the countdown exactly in step with the code instead of lagging it by up to a
     * second depending on when the flow happened to start.
     */
    fun tickerFlow(engine: TotpEngine = TotpEngine()): Flow<Long> = flow {
      while (true) {
        val nowMillis = engine.now().toEpochMilli()
        emit(nowMillis)
        delay(SECOND_MILLIS - (nowMillis % SECOND_MILLIS))
      }
    }
  }
}
