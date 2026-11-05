package com.efprojects.vombatsu_client.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.feed.FeedPost
import com.efprojects.vombatsu_client.feed.FeedRequest
import com.efprojects.vombatsu_client.feed.postsPager
import com.efprojects.vombatsu_client.graphql.SearchTagsQuery
import com.efprojects.vombatsu_client.graphql.SearchUsersQuery
import com.efprojects.vombatsu_client.post.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

enum class SearchTab(@StringRes val title: Int) {
    Posts(R.string.search_posts),
    Users(R.string.search_users),
    Tags(R.string.search_tags),
}

data class UserRow(val login: String, val avatarPath: String?, val rating: String)

data class TagRow(val tag: String, val count: Int)

private const val DEBOUNCE_MS = 400L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val apollo: ApolloClient) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(SearchTab.Posts)
    val tab: StateFlow<SearchTab> = _tab.asStateFlow()

    private val keyword = _query.map { it.trim() }.distinctUntilChanged().debounce(DEBOUNCE_MS)

    val posts: Flow<PagingData<FeedPost>> = keyword
        .flatMapLatest { text ->
            if (text.isEmpty()) {
                flowOf(PagingData.empty())
            } else {
                postsPager(apollo, FeedRequest("new", "all", keyword = text))
            }
        }
        .cachedIn(viewModelScope)

    val users: StateFlow<Loadable<List<UserRow>>> = keyword
        .mapLatest { text -> if (text.isEmpty()) Loadable.Loaded(emptyList()) else searchUsers(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loaded(emptyList()))

    val tags: StateFlow<Loadable<List<TagRow>>> = keyword
        .mapLatest { text -> if (text.isEmpty()) Loadable.Loaded(emptyList()) else searchTags(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loadable.Loaded(emptyList()))

    fun onQuery(text: String) {
        _query.value = text
    }

    fun select(tab: SearchTab) {
        _tab.value = tab
    }

    private suspend fun searchUsers(text: String): Loadable<List<UserRow>> {
        val data = apollo.query(SearchUsersQuery(Optional.present(text))).execute().data?.usersSearch
            ?: return Loadable.Error
        return Loadable.Loaded(
            data.data.mapNotNull { user ->
                user.login?.let { login ->
                    UserRow(login, user.avatar?.takeIf { it.isNotEmpty() }, user.rating_formatted.orEmpty())
                }
            },
        )
    }

    private suspend fun searchTags(text: String): Loadable<List<TagRow>> {
        val data = apollo.query(SearchTagsQuery(Optional.present(text))).execute().data?.tagsSearch
            ?: return Loadable.Error
        return Loadable.Loaded(data.data.mapNotNull { tag -> tag.tag?.let { TagRow(it, tag.count ?: 0) } })
    }
}
