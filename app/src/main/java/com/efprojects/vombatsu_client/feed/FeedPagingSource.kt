package com.efprojects.vombatsu_client.feed

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.graphql.FeedQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

data class FeedRequest(
    val type: String,
    val period: String,
    val tag: String? = null,
    val userId: Int? = null,
    val keyword: String? = null,
)

fun postsPager(apollo: ApolloClient, request: FeedRequest): Flow<PagingData<FeedPost>> =
    Pager(PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false)) {
        FeedPagingSource(apollo, request)
    }.flow

class FeedPagingSource(
    private val apollo: ApolloClient,
    private val request: FeedRequest,
) : PagingSource<Int, FeedPost>() {
    private val seenIds = mutableSetOf<Int>()

    override fun getRefreshKey(state: PagingState<Int, FeedPost>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedPost> {
        val page = params.key ?: 1
        val response = apollo.query(
            FeedQuery(
                page = Optional.present(page),
                type = Optional.present(request.type),
                period = Optional.present(request.period),
                tag = Optional.presentIfNotNull(request.tag),
                userId = Optional.presentIfNotNull(request.userId),
                keyword = Optional.presentIfNotNull(request.keyword),
            ),
        ).execute()
        response.exception?.let { return LoadResult.Error(it) }
        val posts = response.data?.posts
            ?: return LoadResult.Error(IOException(response.errors?.firstOrNull()?.message))
        val items = withContext(Dispatchers.Default) {
            posts.data.orEmpty()
                .mapNotNull { it?.postFields?.toFeedPost() }
                .filter { seenIds.add(it.id) }
        }
        return LoadResult.Page(
            data = items,
            prevKey = null,
            nextKey = if (posts.has_more_pages == true) page + 1 else null,
        )
    }
}
