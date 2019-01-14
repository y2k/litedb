@file:Suppress("UNUSED_VARIABLE", "unused", "UNUSED_PARAMETER")

package y2k.litedb

import android.content.Context
import com.google.gson.Gson
import y2k.litedb.Tree.Leaf
import y2k.litedb.Tree.Node
import java.io.Closeable
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.sql.Connection
import java.sql.DriverManager

interface Connector {
    fun insertJson(sql: String, json: String, arguments: List<Pair<Class<*>, Any>>)
    fun executeSql(sql: String)
    fun selectColumn(sql: String, columnName: String): List<String>
}

class AndroidConnector(context: Context, connectionString: String) : Connector {

    private val database = context.openOrCreateDatabase(connectionString, 0, null)

    override fun insertJson(sql: String, json: String, arguments: List<Pair<Class<*>, Any>>) {
        val bindArgs = listOf(json) + arguments.map { it.second }
        database.execSQL(sql, bindArgs.toTypedArray())
    }

    override fun executeSql(sql: String) =
        database.execSQL(sql)

    override fun selectColumn(sql: String, columnName: String): List<String> =
        database.rawQuery(sql, null).use { cursor ->
            List(cursor.count) {
                cursor.moveToPosition(it)
                cursor.getString(cursor.getColumnIndex(columnName))
            }
        }
}

class DesktopConnector(connString: String) : Connector {

    private val conn = mkConnection(connString)

    private fun mkConnection(connection: String): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:$connection")
    }

    override fun insertJson(sql: String, json: String, arguments: List<Pair<Class<*>, Any>>) {
        val stat = conn.prepareStatement(sql)
        stat.setString(1, json)

        arguments
            .forEachIndexed { index, (clazz, value) ->
                when (clazz.canonicalName) {
                    "java.lang.Integer" -> stat.setInt(index + 2, value as Int)
                    "java.lang.String" -> stat.setString(index + 2, value as String)
                    else -> error("$clazz")
                }
            }

        stat.execute()
        stat.close()
    }

    override fun executeSql(sql: String) {
        conn.createStatement().execute(log(sql))
    }

    override fun selectColumn(sql: String, columnName: String): List<String> {
        val stmt = conn.createStatement()
        val resultSet = stmt.executeQuery(log(sql))
        val jsonColumnIndex = resultSet.findColumn(columnName)
        return resultSet
            .toList {
                val json = it.getString(jsonColumnIndex)
                json
            }
    }
}

@Suppress("RedundantSuspendModifier")
class LiteDb(private val conn: Connector) {

    fun <M : Meta<T>, T : Any> query(meta: M, init: M.() -> Tree, callback: (List<T>) -> Unit): Closeable {
        val where = meta.init()
            .toSqlString()
            .takeIf(String::isNotBlank)
            ?.let { "WHERE $it" }

        createTableIfNotExists(meta)

        conn.selectColumn("SELECT json FROM [${mkTableName(meta)}] $where", "json")
            .map { Gson().fromJson(it, getValueType(meta)) }
            .let { callback(it) }

        return Closeable { }
    }

    fun <T : Any> insert(meta: Meta<T>, value: T) {
        createTableIfNotExists(meta)

        val (params, values) = makeInsertExtraSql(meta)

        val sql = "INSERT INTO [${mkTableName(meta)}] (json $params) VALUES (? $values)"
        conn.insertJson(sql, Gson().toJson(value), meta.javaClass
            .declaredMethods
            .filter { it.name.startsWith("get") }
            .map { it.name to getReturnType(it) }
            .map { (methodName, clazz) ->
                clazz to getValueProp<Any>(value, methodName)
            })
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
        val props = meta
            .javaClass
            .declaredMethods
            .filter { it.name.startsWith("get") }
            .map { it.name to getReturnType(it) }

        val sql = "CREATE TABLE IF NOT EXISTS [${mkTableName(meta)}] (json TEXT ${mkFilterColumns(props)} )"
        conn.executeSql(sql)
    }

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

val EmptyFilter: Any.() -> Tree = { Leaf("'1'", "=", "1") }

// DSL

sealed class Tree {
    class Node(val name: String, vararg val children: Tree) : Tree()
    class Leaf(val attr: String, val operator: String, val value: Any) : Tree()
}

fun Tree.toSqlString(): String = when (this) {
    is Leaf -> "$attr $operator '$value'"
    is Node -> when {
        children.isEmpty() -> ""
        children.size == 1 -> children[0].toSqlString()
        else -> children.joinToString(
            prefix = "(",
            separator = " $name ",
            postfix = ")",
            transform = Tree::toSqlString
        )
    }
}

class ExampleDto(val age: Attr<Int>, val name: Attr<String>)

class Attr<T>(val name: String)

infix fun <T : Any> Attr<T>.eq(value: T): Tree = Leaf(name, "=", value)
infix fun <T : Any> Attr<T>.gtOrEq(value: T): Tree = Leaf(name, ">=", value)
infix fun <T : Any> Attr<T>.ltOrEq(value: T): Tree = Leaf(name, "<=", value)
infix fun <T : Any> Attr<T>.gt(value: T): Tree = Leaf(name, ">", value)
infix fun <T : Any> Attr<T>.lt(value: T): Tree = Leaf(name, "<", value)
infix fun <T : Any> Filterable<T>.eq(value: T): Tree = Leaf(name, "=", value)
infix fun <T : Any> Filterable<T>.gtOrEq(value: T): Tree = Leaf(name, ">=", value)
infix fun <T : Any> Filterable<T>.ltOrEq(value: T): Tree = Leaf(name, "<=", value)
infix fun <T : Any> Filterable<T>.gt(value: T): Tree = Leaf(name, ">", value)
infix fun <T : Any> Filterable<T>.lt(value: T): Tree = Leaf(name, "<", value)
infix fun Filterable<String>.like(value: String): Tree = Leaf(name, "LIKE", value)

fun or(vararg children: Tree): Tree = Node("OR", *children)
fun and(vararg children: Tree): Tree = Node("AND", *children)
