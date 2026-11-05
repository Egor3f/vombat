package com.efprojects.vombatsu_client.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.efprojects.vombatsu_client.battle.BattleRepository
import com.efprojects.vombatsu_client.battle.ThrowResult
import com.efprojects.vombatsu_client.feed.FeedPost
import com.efprojects.vombatsu_client.feed.FeedRequest
import com.efprojects.vombatsu_client.feed.postsPager
import com.efprojects.vombatsu_client.graphql.ProfileQuery
import com.efprojects.vombatsu_client.ignore.IgnoreRepository
import com.efprojects.vombatsu_client.ignore.IgnoredUser
import com.efprojects.vombatsu_client.post.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class Profile(
    val id: Int,
    val login: String,
    val about: String,
    val avatarPath: String?,
    val rating: String,
    val postsAmount: Int,
    val commentsAmount: Int,
    val subscribers: Int,
    val subscriptions: Int,
    val isSubscribed: Boolean,
)

data class ProfileIgnore(val posts: Boolean, val comments: Boolean)

class ProfileViewModel(
    private val apollo: ApolloClient,
    private val ignore: IgnoreRepository,
    private val battle: BattleRepository,
    private val login: String,
) : ViewModel() {
    val bugs: StateFlow<Int?> = battle.bugs

    private val _ignoreState = MutableStateFlow<ProfileIgnore?>(null)
    val ignoreState: StateFlow<ProfileIgnore?> = _ignoreState.asStateFlow()

    private val _profile = MutableStateFlow<Loadable<Profile>>(Loadable.Loading)
    val profile: StateFlow<Loadable<Profile>> = _profile.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: Flow<PagingData<FeedPost>> = _profile
        .filterIsInstance<Loadable.Loaded<Profile>>()
        .flatMapLatest { postsPager(apollo, FeedRequest("new", "all", userId = it.value.id)) }
        .cachedIn(viewModelScope)

    init {
        load()
        viewModelScope.launch { battle.refresh() }
    }

    fun throwBug(userId: Int, onResult: (ThrowResult) -> Unit) {
        viewModelScope.launch { onResult(battle.throwBug(userId)) }
    }

    fun load() {
        _profile.value = Loadable.Loading
        viewModelScope.launch {
            val user = apollo.query(ProfileQuery(login)).execute().data?.user
            val id = user?.id
            _profile.value = if (user != null && id != null) {
                Loadable.Loaded(
                    Profile(
                        id = id,
                        login = user.login ?: login,
                        about = user.about.orEmpty(),
                        avatarPath = user.avatar?.takeIf { it.isNotEmpty() },
                        rating = user.rating_formatted.orEmpty(),
                        postsAmount = user.posts_amount ?: 0,
                        commentsAmount = user.comments_amount ?: 0,
                        subscribers = user.subscribers_count ?: 0,
                        subscriptions = user.subscriptions_count ?: 0,
                        isSubscribed = user.is_my_subscription == true,
                    ),
                )
            } else {
                Loadable.Error
            }
            if (id != null) {
                val entry = ignore.users()?.let { users -> users.firstOrNull { it.userId == id } ?: IgnoredUser(id, login, false, false) }
                _ignoreState.value = entry?.let { ProfileIgnore(it.posts, it.comments) }
            }
        }
    }
}
