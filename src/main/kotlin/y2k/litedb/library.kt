@file:Suppress("UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER")

package y2k.litedb

import java.io.Closeable

interface Connector

object DesktopConnector : Connector

@Suppress("RedundantSuspendModifier")
class LiteDb(private val connector: Connector, s: String) {

    fun <M : Meta<T>, T> query(meta: M, ctx: QueryContext.(M) -> Unit, f: (List<T>) -> Unit): Closeable = TODO()

    suspend fun <R : Meta<T>, T> query(x: R, f: QueryContext.(R) -> Unit): List<T> = TODO()

    suspend fun <T> insert(x: T): Unit = TODO()
    suspend fun <T> insertAll(x: List<T>): Unit = TODO()
}

class QueryProp<T>
interface QueryPropHandler {
    val props: List<QueryProp<*>>
}

interface Meta<T>

interface QueryContext {
    infix fun <T> Prop<T>.eq(value: T): Boolean
    infix fun <T> Prop<T>.lt(value: T): Boolean

    fun and(f: QueryContext.() -> Unit)
}

interface Prop<T>

fun <T> filterable(): Prop<T> = TODO()

/**
 *
 */

/* Models */

class Email(val id: Int, val address: String, val unread: Int) {
    // Мета-информация о "используемых в поиске полях" внутри модели
    companion object : Meta<Email> {
        val address = filterable<String>()
    }
}

class User(
    val id: Int,
    val name: String,
    val lang: String,
    val city: City,
    val age: Int
)

class City(val name: String, val location: List<Float>)

/* External meta-information */

// Мета-информация отдельно от модельки
object UserMeta : Meta<User> {
    val age = filterable<Int>()
    val lang = filterable<String>()
}

/* Example */

suspend fun main(args: Array<String>) {
    val db = LiteDb(DesktopConnector, ":memory:")

    db.insert(mkRandomUser())
    db.insertAll(List(10) { mkRandomUser() })

    val users = // List<User>
        db.query(UserMeta) {
            and {
                it.age lt 20
                it.lang eq "ru"
            }
        }

    val emails = // List<Email>
        db.query(Email) {
            it.address eq "net@net.net"
        }

    val closeable = // Closeable
        db.query(Email, { it.address eq "net@net.net" }) {
            println("Items = $it")
        }
}

fun mkRandomUser(): User = TODO()
