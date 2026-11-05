package com.efprojects.vombatsu_client.feed

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.efprojects.vombatsu_client.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

enum class FeedType(val type: String, val period: String, @StringRes val title: Int) {
    Hot("hot", "week", R.string.feed_hot),
    Best("best", "week", R.string.feed_best),
    New("new", "all", R.string.feed_new),
    Subscriptions("subscriptions", "all", R.string.feed_subscriptions),
}

class FeedViewModel(private val apollo: ApolloClient) : ViewModel() {
    private val _selected = MutableStateFlow(FeedType.Hot)
    val selected: StateFlow<FeedType> = _selected.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: Flow<PagingData<FeedPost>> = _selected
        .flatMapLatest { feed -> postsPager(apollo, FeedRequest(feed.type, feed.period)) }
        .cachedIn(viewModelScope)

    fun select(feed: FeedType) {
        _selected.value = feed
    }
}
