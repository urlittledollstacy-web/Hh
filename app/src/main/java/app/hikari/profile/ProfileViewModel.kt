package app.hikari.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hikari.data.remote.AniListGraphQlService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: AniListGraphQlService,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.loading || _state.value.profile != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.viewerProfile() }
                .onSuccess { _state.value = ProfileUiState(profile = it) }
                .onFailure { _state.value = ProfileUiState(error = "Couldn't load your AniList profile. Try again.") }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = null)
            runCatching { api.viewerProfile() }
                .onSuccess { _state.value = ProfileUiState(profile = it) }
                .onFailure { _state.value = _state.value.copy(refreshing = false, error = "Couldn't refresh your AniList profile.") }
        }
    }
}

data class ProfileUiState(
    val profile: AniListProfile? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
)
