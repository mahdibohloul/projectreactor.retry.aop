package io.github.mahdibohloul.projectreactor.retry.aop.kotlin

import io.github.mahdibohloul.projectreactor.retry.aop.annotation.EnableReactiveRetry
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRecover
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

private val RECOVER_TRACE = ThreadLocal<String?>()

/** Copies a value into [RECOVER_TRACE] while the coroutine runs, like an MDC element. */
private class RecoverTraceElement(private val value: String) :
    ThreadContextElement<String?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RecoverTraceElement>

    override fun updateThreadContext(context: CoroutineContext): String? {
        val old = RECOVER_TRACE.get()
        RECOVER_TRACE.set(value)
        return old
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) = RECOVER_TRACE.set(oldState)
}

/**
 * `@ReactiveRecover` methods used from Kotlin.
 */
class ReactiveRecoverKotlinTests {

    @Configuration
    @EnableReactiveRetry
    class Config {
        @Bean
        fun recoverService() = RecoverService()
    }

    @Service
    class RecoverService {
        val calls = AtomicInteger()
        val recoverCalls = AtomicInteger()

        @ReactiveRetryable(maxAttempts = 2)
        suspend fun load(id: String): String {
            calls.incrementAndGet()
            delay(1)
            throw IOException("down $id")
        }

        @ReactiveRecover
        suspend fun recoverLoad(error: IOException, id: String): String {
            recoverCalls.incrementAndGet()
            delay(1)
            return "recovered $id: ${error.message}"
        }

        @ReactiveRetryable(maxAttempts = 1, recover = "named")
        suspend fun byName(number: Int): Int {
            calls.incrementAndGet()
            throw IllegalStateException("by name")
        }

        @ReactiveRecover
        suspend fun named(number: Int): Int = number * 10

        @ReactiveRetryable(maxAttempts = 1)
        suspend fun context(number: Long): String {
            calls.incrementAndGet()
            throw IllegalStateException("context")
        }

        @ReactiveRecover
        suspend fun recoverContext(error: IllegalStateException, number: Long): String =
            "${currentCoroutineContext()[CoroutineName]?.name}/${RECOVER_TRACE.get()}/$number"

        @ReactiveRetryable(maxAttempts = 1)
        suspend fun recoverThrows(flag: Boolean): String {
            calls.incrementAndGet()
            throw IllegalStateException("retryable")
        }

        @ReactiveRecover
        suspend fun throwingRecover(error: IllegalStateException, flag: Boolean): String {
            delay(1)
            throw IllegalArgumentException("recover failed")
        }

        @ReactiveRetryable(maxAttempts = 2)
        fun mono(id: String): Mono<String> {
            calls.incrementAndGet()
            return Mono.error(IllegalStateException("mono $id"))
        }

        @ReactiveRecover
        fun recoverMono(error: IllegalStateException, id: String): Mono<String> = Mono.just("recovered ${error.message}")
    }

    private fun withService(block: (RecoverService) -> Unit) {
        AnnotationConfigApplicationContext(Config::class.java).use { block(it.getBean<RecoverService>()) }
    }

    @Test
    fun `a suspend recover function recovers a suspend function`() = withService { service ->
        val result = runBlocking { service.load("a") }
        assertEquals("recovered a: down a", result)
        assertEquals(3, service.calls.get())
        assertEquals(1, service.recoverCalls.get())
    }

    @Test
    fun `a suspend recover function is found by name`() = withService { service ->
        assertEquals(70, runBlocking { service.byName(7) })
        assertEquals(2, service.calls.get())
    }

    @Test
    fun `the recover function sees the coroutine context of the caller`() = withService { service ->
        val result = runBlocking(CoroutineName("caller") + RecoverTraceElement("trace-1")) { service.context(5) }
        assertEquals("caller/trace-1/5", result)
    }

    @Test
    fun `the recover function sees the coroutine context on another dispatcher`() = withService { service ->
        val result = runBlocking {
            withContext(Dispatchers.IO + CoroutineName("io-caller") + RecoverTraceElement("trace-io")) {
                service.context(6)
            }
        }
        assertEquals("io-caller/trace-io/6", result)
    }

    @Test
    fun `an error of a suspend recover function goes to the caller`() = withService { service ->
        val error = assertThrows(IllegalArgumentException::class.java) { runBlocking { service.recoverThrows(true) } }
        assertEquals("recover failed", error.message)
    }

    @Test
    fun `a recovered Mono is used from a coroutine`() = withService { service ->
        val result = runBlocking { service.mono("m").awaitSingle() }
        assertEquals("recovered mono m", result)
        assertEquals(3, service.calls.get())
    }
}
