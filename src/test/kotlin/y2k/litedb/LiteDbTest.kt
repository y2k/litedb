package y2k.litedb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class LiteDbTest {

    @Test
    fun `test no filter`() = runSuspend {
        val expected = List(10, ::mkRandomUser)

        val db = LiteDb(DesktopConnector, ":memory:")
        db.insertAll(UserMeta, expected)

        val actual = query(db, UserMeta, EmptyFilter)

        assertEquals(expected, actual)
    }

    @Test
    fun `test simple filter`() = runSuspend {
        val expected = List(10, ::mkRandomUser)

        val db = LiteDb(DesktopConnector, ":memory:")
        db.insertAll(UserMeta, expected)

        val actual = query(db, UserMeta) {
            age gtOrEq 50
        }

        assertEquals(actual, expected.filter { it.age >= 50 })
    }

    @Test
    fun `test complex filter`() = runSuspend {
        val expected = List(10, ::mkRandomUser)

        val db = LiteDb(DesktopConnector, ":memory:")
        db.insertAll(UserMeta, expected)

        val actual = query(db, UserMeta) {
            or(
                age ltOrEq 20,
                age gtOrEq 50
            )
        }

        assertEquals(actual, expected.filter { it.age <= 20 || it.age >= 50 })
    }

    private suspend fun <M : Meta<T>, T : Any> query(db: LiteDb, meta: M, init: M.() -> Tree): List<T> =
        suspendCoroutine { continuation ->
            db.query(meta, init, continuation::resume)
        }

    private fun mkRandomUser(id: Int): User =
        User(
            id,
            "name #${Random.nextInt(10000)}",
            "lang #${Random.nextInt(100)}",
            City("name #${Random.nextInt(10000)}", emptyList()),
            Random.nextInt(150)
        )
}

data class User(
    val id: Int,
    val name: String,
    val lang: String,
    val city: City,
    val age: Int
)

data class City(val name: String, val location: List<Float>)
