package com.efprojects.vombatsu_client.post

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentTreeTest {
    private fun comment(id: Int) = Comment(id, "u", null, 0, 0, emptyList(), null, null, emptyList(), false)
    private val comments = listOf(1, 2, 3, 4, 5).map(::comment)
    private val sorting = """{"3":{"1":{"5":null},"4":null},"2":null}"""

    @Test
    fun `sorting order depth and descendants`() {
        val nodes = buildCommentTree(sorting, comments)
        assertEquals(listOf(3, 1, 5, 4, 2), nodes.map { it.comment.id })
        assertEquals(listOf(0, 1, 2, 1, 0), nodes.map { it.depth })
        assertEquals(listOf(3, 1, 0, 0, 0), nodes.map { it.descendants })
    }

    @Test
    fun `comments missing from sorting are kept and unknown ids skipped`() {
        val nodes = buildCommentTree("""{"99":null,"2":null}""", listOf(comment(1), comment(2)))
        assertEquals(listOf(2, 1), nodes.map { it.comment.id })
        assertEquals(listOf(1, 2), buildCommentTree(null, listOf(comment(1), comment(2))).map { it.comment.id })
    }

    @Test
    fun `comment path contains the comment and its ancestors only`() {
        val nodes = buildCommentTree(
            sorting,
            listOf(comment(3), comment(1).copy(parentId = 3), comment(5).copy(parentId = 1), comment(2)),
        )
        assertEquals(setOf(5, 1, 3), commentPath(nodes, 5))
        assertEquals(setOf(2), commentPath(nodes, 2))
        assertEquals(emptySet<Int>(), commentPath(nodes, 99))
    }

    @Test
    fun `collapse hides only the subtree`() {
        val nodes = buildCommentTree(sorting, comments)
        assertEquals(listOf(3, 1, 4, 2), visibleComments(nodes, setOf(1)).map { it.comment.id })
        assertEquals(listOf(3, 2), visibleComments(nodes, setOf(3)).map { it.comment.id })
        assertEquals(listOf(3, 1, 5, 4, 2), visibleComments(nodes, emptySet()).map { it.comment.id })
    }
}
