package y2k.litedb

import java.sql.ResultSet
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun <M : Meta<T>, T : Any> LiteDb.query(meta: M, init: M.() -> Tree): List<T> =
    suspendCoroutine { cont ->
        query(meta, init) {
            cont.resume(it)
        }.close()
    }

fun <T : Any> LiteDb.insertAll(meta: Meta<T>, xs: List<T>) =
    xs.forEach { insert(meta, it) }

fun <T : Any> ResultSet.toList(f: (ResultSet) -> T): List<T> =
    use {
        val r = ArrayList<T>()
        while (it.next()) r.add(f(it))
        r
    }
