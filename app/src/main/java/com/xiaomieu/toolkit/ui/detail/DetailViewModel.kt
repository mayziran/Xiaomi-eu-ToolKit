package com.xiaomieu.toolkit.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaomieu.toolkit.data.AppInfoResolver
import com.xiaomieu.toolkit.data.MiPushScanner
import com.xiaomieu.toolkit.data.XmsfRegistryRepository
import com.xiaomieu.toolkit.data.model.RegisteredApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Ready(val app: RegisteredApp?) : DetailUiState
    data class Failed(val message: String) : DetailUiState
}

class DetailViewModel(
    app: Application,
    private val packageName: String,
) : AndroidViewModel(app) {

    private val repository = XmsfRegistryRepository(AppInfoResolver.shared(app), MiPushScanner(app))

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = try {
                val snapshot = repository.loadSnapshot()
                DetailUiState.Ready(snapshot.apps.firstOrNull { it.packageName == packageName })
            } catch (t: Throwable) {
                DetailUiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}

class DetailViewModelFactory(
    private val application: Application,
    private val packageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DetailViewModel(application, packageName) as T
}
