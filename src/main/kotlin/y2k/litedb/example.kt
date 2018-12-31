package y2k.litedb

/* Models */

data class Email(val id: Int, val address: String, val unread: Int) {
    // Мета-информация о "используемых в поиске полях" внутри модели
    companion object : Meta<Email> {
        val address = filter<String>("address")
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
    val age = filter<Int>("age")
    val lang = filter<String>("lang")
    val name = filter<String>("name")
}

/* Example */

suspend fun main(args: Array<String>) {
    val db = LiteDb(DesktopConnector, ":memory:")

    db.insert(UserMeta, mkRandomUser())
    db.insertAll(UserMeta, List(10) { mkRandomUser() })

    val closeable = // Closeable
        db.query(
            UserMeta, {
                or(
                    name eq "alice",
                    and(
                        age gt 50,
                        lang eq "ru"
                    )
                )
            }) {
            println("User #1:\n\t${it.joinToString(separator = "\n\t")}")
        }

    val users = // List<User>
        db.query(UserMeta) {
            age gt 50
        }
    println("User #2:\n\t${users.joinToString(separator = "\n\t")}")

    val emails = // List<Email>
        db.query(Email) {
            address eq "net@net.net"
        }
    println("Emails:\n\t${emails.joinToString(separator = "\n\t")}")
}
