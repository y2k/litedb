package y2k.litedb

import y2k.litedb.Tree.Leaf
import y2k.litedb.Tree.Node

sealed class Tree {
    class Node(val name: String, vararg val children: Tree) : Tree()
    class Leaf(val attr: String, val operator: String, val value: Any) : Tree()
}

fun main(args: Array<String>) {

    select(
        Leaf("name", "=", "alice")
    )

    select(
        Node(
            "AND",
            Leaf("age", ">=", 16),
            Leaf("age", "<=", 18)
        )
    )

    /* ... WHERE name = 'alice' OR (age >= 16 AND age <= 18) */

    select(
        Node(
            "OR",
            Leaf("name", "=", "alice"),
            Node(
                "AND",
                Leaf("age", ">=", 21),
                Leaf("age", "<=", 150)
            )
        )
    )

    select {
        or(
            name eq "alice",
            and(
                age gtOrEq 21,
                age ltOrEq 150
            )
        )
    }.let(::println)

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

fun or(vararg children: Tree): Tree = Node("OR", *children)
fun and(vararg children: Tree): Tree = Node("AND", *children)

private fun select(node: Tree): Unit = Unit
private fun select(init: ExampleDto.() -> Tree): String =
    ExampleDto(Attr("age"), Attr("name")).init().toSqlString()
