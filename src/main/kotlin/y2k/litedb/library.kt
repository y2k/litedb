@file:Suppress("UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER")

package y2k.litedb

import com.google.gson.Gson
import java.io.Closeable
import java.lang.reflect.Method
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
class LiteDb(connector: Connector, connString: String) {

    private val conn = connector.mkConnection(connString)

    fun <M : Meta<T>, T : Any> query(meta: M, init: M.() -> Tree, callback: (List<T>) -> Unit): Closeable {
        val where = meta.init()
            .toSqlString()
            .takeIf(String::isNotBlank)
            ?.let { "WHERE $it" }

        createTableIfNotExists(meta)

        val stmt = conn.createStatement()
        val sql = "SELECT * FROM [${mkTableName(meta)}] $where"

        val items = stmt
            .executeQuery(log(sql))
            .toList {
                val json = it.getString(it.findColumn("json"))
                Gson().fromJson(json, getValueType(meta))
            }
        callback(items)

        return Closeable { }
    }

    fun <T : Any> insert(meta: Meta<T>, value: T) {
        createTableIfNotExists(meta)

        val (params, values) = makeInsertExtraSql(meta)

        val sql = "INSERT INTO [${mkTableName(meta)}] (json $params) VALUES (? $values)"
        val s = conn.prepareStatement(sql)
        s.setString(1, Gson().toJson(value))

        meta.javaClass
            .declaredMethods
            .filter { it.name.startsWith("get") }
            .map { it.name to getReturnType(it) }
            .forEachIndexed { index, x ->
                when (x.second.canonicalName) {
                    "java.lang.Integer" -> s.setInt(index + 2, getValueProp(value, x.first))
                    "java.lang.String" -> s.setString(index + 2, getValueProp(value, x.first))
                    else -> error("${x.second}")
                }
            }

        s.execute()
        s.close()
    }

    private fun makeInsertExtraSql(meta: Meta<*>): Pair<String, String> {
        val props = meta
            .javaClass
            .declaredMethods
            .filter { it.name.startsWith("get") }
        if (props.isEmpty()) return "" to ""

        val params = props.joinToString(prefix = ", ") { toPropName(it.name) }
        val values = props.joinToString(prefix = ", ") { "?" }
        return params to values
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> getValueProp(value: Any, methodName: String): R {
        val m = value.javaClass.declaredMethods.first { it.name == methodName }
        val r = m(value)
        return r as R
    }

    private fun <T : Any> createTableIfNotExists(meta: Meta<T>) {
        val stmt = conn.createStatement()

        val props = meta
            .javaClass
            .declaredMethods
            .filter { it.name.startsWith("get") }
            .map { it.name to getReturnType(it) }

        val sql = "CREATE TABLE IF NOT EXISTS [${mkTableName(meta)}] (json TEXT ${mkFilterColumns(props)} )"
        stmt.execute(log(sql))
    }

    private fun log(sql: String): String =
        sql.also(::println)

    private fun mkFilterColumns(props: List<Pair<String, Class<*>>>): String =
        if (props.isEmpty()) ""
        else props.joinToString(prefix = ", ") { "${toPropName(it.first)} ${toSqlType(it.second)}" }

    private fun toPropName(methodName: String): String =
        methodName.substring(3).toLowerCase()

    private fun toSqlType(type: Class<*>): String =
        when (type.canonicalName) {
            "java.lang.Integer" -> "NUMBER"
            "java.lang.String" -> "TEXT"
            else -> error("$type")
        }

    private fun getReturnType(it: Method): Class<*> {
        val c = it.toGenericString()
        return c.substring(c.indexOf('<') + 1, c.indexOf('>'))
            .let { Class.forName(it) }
    }

    private fun <T : Any> mkTableName(value: Meta<T>): String =
        getValueType(value).simpleName

    @Suppress("UNCHECKED_CAST")
    private fun <M : Meta<T>, T> getValueType(meta: M): Class<T> =
        (meta.javaClass.genericInterfaces[0] as ParameterizedType).actualTypeArguments[0] as Class<T>
}

interface Meta<T>

class Filterable<T>(val name: String)

fun <T> filter(name: String): Filterable<T> = Filterable(name)

val EmptyFilter: Any.() -> Tree = { Tree.Leaf("'1'", "=", "1") }
