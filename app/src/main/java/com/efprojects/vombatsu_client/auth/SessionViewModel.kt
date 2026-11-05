package com.efprojects.vombatsu_client.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(private val auth: AuthRepository) : ViewModel() {
    val session = auth.session

    private val _savingFilters = MutableStateFlow(false)
    val savingFilters: StateFlow<Boolean> = _savingFilters.asStateFlow()

    init {
        viewModelScope.launch { auth.refresh() }
    }

    fun saveFilters(filters: FeedFilters, onResult: (Boolean) -> Unit) {
        if (_savingFilters.value) return
        _savingFilters.value = true
        viewModelScope.launch {
            val saved = auth.saveFilters(filters)
            _savingFilters.value = false
            onResult(saved)
        }
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
