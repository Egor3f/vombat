package com.efprojects.vombatsu_client.ignore

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.efprojects.vombatsu_client.graphql.IgnoredTagsQuery
import com.efprojects.vombatsu_client.graphql.IgnoredUsersQuery

data class IgnoredUser(val userId: Int, val login: String, val posts: Boolean, val comments: Boolean)

class IgnoreRepository(private val apollo: ApolloClient) {
    suspend fun users(): List<IgnoredUser>? {
        val result = mutableListOf<IgnoredUser>()
        var page = 1
        while (true) {
            val data = apollo.query(IgnoredUsersQuery(Optional.present(page))).execute().data?.userIgnore
                ?: return null
            data.data.mapNotNullTo(result) { user ->
                user.ignored_user_id?.let { id ->
                    IgnoredUser(id, user.login.orEmpty(), user.posts == true, user.comments == true)
                }
            }
            if (!data.has_more_pages) return result
            page++
        }
    }

    suspend fun tags(): List<String>? {
        val result = mutableListOf<String>()
        var page = 1
        while (true) {
            val data = apollo.query(IgnoredTagsQuery(Optional.present(page))).execute().data?.tagIgnore
                ?: return null
            data.data.mapNotNullTo(result) { it.tag }
            if (!data.has_more_pages) return result
            page++
        }
    }
}
