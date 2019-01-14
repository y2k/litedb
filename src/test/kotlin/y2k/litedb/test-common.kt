package y2k.litedb

import org.junit.jupiter.api.Assertions
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun runTest(block: suspend () -> Unit) {
    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1)) {
        val run = RunSuspend()
        block.startCoroutine(run)
        run.await()
    }
}

private class RunSuspend : Continuation<Unit> {
    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    var result: Any? = null

    override fun resumeWith(result: Result<Unit>) = synchronized(this) {
        this.result = result
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
    }

    fun await() = synchronized(this) {
        while (true) {
            when (val result = this.result) {
                null -> @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).wait()
                else -> {
                    (result as Result<*>).getOrThrow() // throw up failure
                    return
                }
            }
        }
    }
}
