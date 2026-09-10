package app.hikari.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.data.AniListLibraryRepository
import app.hikari.data.remote.AniListGraphQlService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: AniListGraphQlService,
    private val library: AniListLibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private var profileSessionVersion = library.sessionVersion.value

    init {
        viewModelScope.launch {
            library.changes.collect {
                if (_state.value.profile != null) refresh()
            }
        }
        viewModelScope.launch {
            library.sessionVersion.drop(1).collect(::resetForSession)
        }
    }

    fun load() {
        resetForSession(library.sessionVersion.value)
        if (_state.value.loading || _state.value.profile != null) return
        loadProfile(isRefresh = false)
    }

    fun refresh() = loadProfile(isRefresh = true)

    private fun loadProfile(isRefresh: Boolean) {
        val requestGeneration = ++generation
        loadJob?.cancel()
        _state.value = _state.value.copy(
            loading = !isRefresh && _state.value.profile == null,
            refreshing = isRefresh,
            error = null,
        )
        loadJob = viewModelScope.launch {
            try {
                val profile = api.viewerProfile()
                if (requestGeneration == generation) {
                    _state.value = ProfileUiState(profile = profile)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (requestGeneration == generation) {
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = if (isRefresh) {
                            "Couldn't refresh your AniList profile."
                        } else {
                            "Couldn't load your AniList profile. Try again."
                        },
                    )
                }
            }
        }
    }

    private fun resetForSession(version: Long) {
        if (profileSessionVersion == version) return
        profileSessionVersion = version
        generation++
        loadJob?.cancel()
        _state.value = ProfileUiState()
    }
}

data class AniListProfile(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val about: String?,
    val animeCount: Int,
    val episodesWatched: Int,
    val daysWatched: Double,
    val animeMeanScore: Double,
    val mangaCount: Int,
    val chaptersRead: Int,
    val volumesRead: Int,
    val daysRead: Double,
    val mangaMeanScore: Double,
)

data class ProfileUiState(
    val profile: AniListProfile? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)
