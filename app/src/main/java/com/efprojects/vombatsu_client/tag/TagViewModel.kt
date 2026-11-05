package com.efprojects.vombatsu_client.tag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.feed.FeedPost
import com.efprojects.vombatsu_client.feed.FeedRequest
import com.efprojects.vombatsu_client.feed.postsPager
import com.efprojects.vombatsu_client.graphql.TagStateQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TagState(val subscribed: Boolean, val ignored: Boolean)

class TagViewModel(apollo: ApolloClient, tag: String) : ViewModel() {
    val posts: Flow<PagingData<FeedPost>> =
        postsPager(apollo, FeedRequest("new", "all", tag = tag)).cachedIn(viewModelScope)

    private val _state = MutableStateFlow<TagState?>(null)
    val state: StateFlow<TagState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val response = apollo.query(TagStateQuery(Optional.present(tag))).execute()
            if (response.exception != null) return@launch
            val data = response.data
            _state.value = TagState(
                subscribed = data?.tagSubscriptions?.data.orEmpty().any { it.tag == tag },
                ignored = data?.tagIgnore?.data.orEmpty().any { it.tag == tag },
            )
        }
    }
}
