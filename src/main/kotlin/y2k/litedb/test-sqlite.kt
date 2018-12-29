package y2k.litedb

import com.google.gson.Gson
import java.sql.DriverManager

fun main(args: Array<String>) {
    Class.forName("org.sqlite.JDBC")
    val conn = DriverManager.getConnection("jdbc:sqlite::memory:")

    val stmt = conn.createStatement()
    stmt.execute("CREATE TABLE users (json TEXT)")

    List(10) { mkRandomUser() }
        .map { Gson().toJson(it) }
        .forEach {
            val s = conn.prepareStatement("INSERT INTO users (json) VALUES (?)")
            s.setString(1, it)
            s.execute()
            s.close()
        }

    val users = stmt
        .executeQuery("SELECT * FROM users")
        .toList {
            val json = it.getString(1)
            Gson().fromJson(json, User::class.java)
        }

    conn.close()
    println("Count = $users")
}
