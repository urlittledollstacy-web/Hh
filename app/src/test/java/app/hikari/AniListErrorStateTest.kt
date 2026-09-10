package app.hikari

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.hikari.core.auth.SecureTokenStore
import app.hikari.core.model.MediaType
import app.hikari.data.remote.AniListGraphQlService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AniListErrorStateTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var tokenStore: SecureTokenStore

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        tokenStore = SecureTokenStore(ApplicationProvider.getApplicationContext<Context>())
        tokenStore.clear()
    }

    @After
    fun tearDown() {
        tokenStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `HTTP 403 is exposed as a recoverable failure`() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { serviceReturning(403).trending(MediaType.ANIME) }
        }

        assertEquals("AniList API temporarily unavailable (403)", error.message)
    }

    @Test
    fun `Home completes loading and exposes its error for HTTP 403`() = runBlocking {
        val viewModel = HomeViewModel(serviceReturning(403))

        awaitState { !viewModel.state.value.loading && !viewModel.state.value.refreshing }

        assertEquals("AniList is unavailable right now. Try again in a moment.", viewModel.state.value.error)
    }

    @Test
    fun `Discover completes searching and exposes its error for HTTP 403`() = runBlocking {
        val viewModel = SearchViewModel(serviceReturning(403))

        viewModel.setQuery("Frieren")
        awaitState { !viewModel.state.value.searching && viewModel.state.value.error != null }

        assertFalse(viewModel.state.value.loadingMore)
        assertEquals("Couldn't search AniList. Try again.", viewModel.state.value.error)
    }

    @Test
    fun `actual cancellation continues to propagate`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { serviceThrowing(CancellationException("cancelled")).trending(MediaType.ANIME) }
        }
    }

    private fun serviceReturning(code: Int): AniListGraphQlService = serviceWithInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body("".toResponseBody())
            .build()
    }

    private fun serviceThrowing(error: CancellationException): AniListGraphQlService =
        serviceWithInterceptor { throw error }

    private fun serviceWithInterceptor(interceptor: Interceptor): AniListGraphQlService =
        AniListGraphQlService(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            tokenStore = tokenStore,
        )

    private suspend fun awaitState(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) return
            delay(10)
        }
        error("ViewModel did not reach the expected state")
    }
}
