package com.efprojects.vombatsu_client.ignore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.efprojects.vombatsu_client.actions.ActionsRepository
import com.efprojects.vombatsu_client.post.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IgnoreListViewModel(
    private val repository: IgnoreRepository,
    private val actions: ActionsRepository,
) : ViewModel() {
    private val _users = MutableStateFlow<Loadable<List<IgnoredUser>>>(Loadable.Loading)
    val users: StateFlow<Loadable<List<IgnoredUser>>> = _users.asStateFlow()

    private val _tags = MutableStateFlow<Loadable<List<String>>>(Loadable.Loading)
    val tags: StateFlow<Loadable<List<String>>> = _tags.asStateFlow()

    init {
        load()
    }

    fun load() {
        _users.value = Loadable.Loading
        _tags.value = Loadable.Loading
        viewModelScope.launch {
            _users.value = repository.users()?.let { Loadable.Loaded(it) } ?: Loadable.Error
        }
        viewModelScope.launch {
            _tags.value = repository.tags()?.let { Loadable.Loaded(it) } ?: Loadable.Error
        }
    }

    fun removeTag(tag: String, onFailure: () -> Unit) {
        val current = (_tags.value as? Loadable.Loaded)?.value ?: return
        _tags.value = Loadable.Loaded(current - tag)
        actions.ignoreTag(tag, add = false) {
            _tags.value = Loadable.Loaded(current)
            onFailure()
        }
    }
}
