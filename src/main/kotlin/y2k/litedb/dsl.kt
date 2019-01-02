package y2k.litedb

import y2k.litedb.Tree.Leaf
import y2k.litedb.Tree.Node

private fun select(node: Tree): Unit = Unit
private fun select(init: ExampleDto.() -> Tree): String =
    ExampleDto(Attr("age"), Attr("name")).init().toSqlString()

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
