package y2k.litedb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import y2k.litedb.LiteDbTestUtils.User
import y2k.litedb.LiteDbTestUtils.UserMeta
import y2k.litedb.LiteDbTestUtils.mkRandomList
import y2k.litedb.LiteDbTestUtils.querySuspend
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class LiteDbTest {

    @Test
    fun `delete with filter should success`() = runTest {
        val filter: (User) -> Boolean = { it.id < 10 }
        val sqlFilter: UserMeta.() -> Tree = { id lt 10 }

        val expected = mkRandomList()

        val db = LiteDb(DesktopConnector(":memory:"))
        db.insertAll(UserMeta, expected)

        db.delete(UserMeta, sqlFilter)

        val actual = db.querySuspend(UserMeta, EmptyFilter)
        assertEquals(expected.filterNot(filter), actual)
    }

    @Test
    fun `eq filter should success`() = runTest {
        val example = mkRandomList()
        assertQuery(example,
            { it.name == example[5].name },
            { name eq example[5].name })
    }

    @Test
    fun `test no filter`() = runTest {
        assertQuery(mkRandomList(), { true }, EmptyFilter)
    }

    @Test
    fun `test simple filter`() = runTest {
        assertQuery(mkRandomList(),
            { it.id >= 5 },
            { id gtOrEq 5 })
    }

    @Test
    fun `test complex filter`() = runTest {
        assertQuery(mkRandomList(),
            { it.id <= 2 || it.id >= 8 },
            { or(id ltOrEq 2, id gtOrEq 8) })
    }

    private suspend fun assertQuery(expected: List<User>, filter: (User) -> Boolean, sqlFilter: UserMeta.() -> Tree) {
        val db = LiteDb(DesktopConnector(":memory:"))
        db.insertAll(UserMeta, expected)

        val actual = db.querySuspend(UserMeta, sqlFilter)

        assertEquals(expected.filter(filter), actual)
    }
}

object LiteDbTestUtils {

    fun mkRandomList(): List<User> = List(20, ::mkRandomUser)

    suspend fun <M : Meta<T>, T : Any> LiteDb.querySuspend(meta: M, init: M.() -> Tree): List<T> =
        suspendCoroutine { continuation ->
            query(meta, init, continuation::resume)
        }

    private fun mkRandomUser(id: Int): User =
        User(
            id,
            "name #${Random.nextInt(10000)}",
            "lang #${Random.nextInt(100)}",
            City("name #${Random.nextInt(10000)}", emptyList()),
            Random.nextInt(150)
        )

    object UserMeta : Meta<User> {
        val id = filter<Int>("id")
        val name = filter<String>("name")
    }

    data class User(
        val id: Int,
        val name: String,
        val lang: String,
        val city: City,
        val age: Int
    )

    data class City(val name: String, val location: List<Float>)
}
