@file:Suppress("UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER")

package y2k.litedb

import com.google.gson.Gson
import java.io.Closeable
import java.lang.reflect.ParameterizedType
import java.sql.Connection
import java.sql.DriverManager

interface Connector {
    fun mkConnection(connection: String): Connection
}

object DesktopConnector : Connector {
    override fun mkConnection(connection: String): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:$connection")
    }
}

@Suppress("RedundantSuspendModifier")
class LiteDb(connector: Connector, conn: String) {

    private val conn = connector.mkConnection(conn)

    fun <M : Meta<T>, T : Any> query(meta: M, ctx: QueryContext.(M) -> Unit, callback: (List<T>) -> Unit): Closeable {
        val stmt = conn.createStatement()
        val items = stmt
            .executeQuery("SELECT * FROM [${mkTableName(meta)}]")
            .toList {
                val json = it.getString(it.findColumn("json"))
                Gson().fromJson(json, getValueType(meta))
            }
        callback(items)

        return Closeable { }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <M : Meta<T>, T> getValueType(meta: M): Class<T> =
        (meta.javaClass.genericInterfaces[0] as ParameterizedType).actualTypeArguments[0] as Class<T>

    fun <T : Any> insert(meta: Meta<T>, value: T) {
        createTableIfNotExists(meta)

        val s = conn.prepareStatement("INSERT INTO [${mkTableName(meta)}] (json) VALUES (?)")
        s.setString(1, Gson().toJson(value))
        s.execute()
        s.close()
    }

    private fun <T : Any> createTableIfNotExists(meta: Meta<T>) {
        val stmt = conn.createStatement()
        stmt.execute("CREATE TABLE IF NOT EXISTS [${mkTableName(meta)}] (json TEXT)")
    }

    private fun mkTableName(value: Meta<*>): String =
        value.javaClass.simpleName
}

class QueryProp<T>

interface Meta<T>

interface QueryContext {
    infix fun <T> Filterable<T>.eq(value: T): Boolean
    infix fun <T> Filterable<T>.lt(value: T): Boolean
    fun and(f: QueryContext.() -> Unit)
}

class Filterable<T>

fun <T> filterable(): Filterable<T> = Filterable()

/**
 *
 */

/* Models */

data class Email(val id: Int, val address: String, val unread: Int) {
    // Мета-информация о "используемых в поиске полях" внутри модели
    companion object : Meta<Email> {
        val address = filterable<String>()
    }
}

data class User(
    val id: Int,
    val name: String,
    val lang: String,
    val city: City,
    val age: Int
)

data class City(val name: String, val location: List<Float>)

/* External meta-information */

// Мета-информация отдельно от модельки
object UserMeta : Meta<User> {
    val age = filterable<Int>()
    val lang = filterable<String>()
}

/* Example */

suspend fun main(args: Array<String>) {
    val db = LiteDb(DesktopConnector, ":memory:")

    db.insert(UserMeta, mkRandomUser())
    db.insertAll(UserMeta, List(10) { mkRandomUser() })

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
