package com.stitch.authenticator.ui.main

import com.stitch.authenticator.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scaffolded version of this test asserted `uiState.first() == Loading`.
 *
 * That is a race, not a test: `uiState` is `stateIn(viewModelScope, WhileSubscribed(5000), Loading)`, so
 * `first()` returns whatever the shared StateFlow happens to hold — `Loading` only until the repository's
 * flow has been collected. It passed twice by luck and then failed.
 *
 * Every case below asserts something that cannot depend on scheduling order. [Dispatchers.setMain] also
 * gives each test a fresh test scheduler, so a ViewModel from one test can never pollute the next.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_isLoading_beforeCollection() {
    val viewModel = MainScreenViewModel(FakeDataRepository())

    // `.value` never subscribes, so the sharing coroutine cannot have run yet: deterministic.
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_emitsSuccess_forRepositoryData() = runTest {
    val viewModel = MainScreenViewModel(FakeDataRepository())

    // Wait for the real value instead of sampling whichever one is current.
    assertEquals(
      MainScreenUiState.Success(listOf("Sample")),
      viewModel.uiState.first { it !is MainScreenUiState.Loading },
    )
  }

  @Test
  fun uiState_emitsError_whenRepositoryFails() = runTest {
    val viewModel = MainScreenViewModel(FailingDataRepository())

    val state = viewModel.uiState.first { it !is MainScreenUiState.Loading }
    assertTrue("expected Error but was $state", state is MainScreenUiState.Error)
  }
}

private class FakeDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}

private class FailingDataRepository : DataRepository {
  override val data: Flow<List<String>> = flow { throw IllegalStateException("repository boom") }
}
