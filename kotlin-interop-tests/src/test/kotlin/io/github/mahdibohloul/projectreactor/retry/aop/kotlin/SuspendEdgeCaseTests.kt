package io.github.mahdibohloul.projectreactor.retry.aop.kotlin

import io.github.mahdibohloul.projectreactor.retry.aop.annotation.EnableReactiveRetry
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryInterceptorBuilder
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.Advised
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

private val TRACE = ThreadLocal<String?>()

/** Copies a value into [TRACE] while the coroutine runs, like an MDC element. */
private class TraceElement(private val value: String) :
    ThreadContextElement<String?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TraceElement>

    override fun updateThreadContext(context: CoroutineContext): String? {
        val old = TRACE.get()
        TRACE.set(value)
        return old
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: String?) = TRACE.set(oldState)
}

/**
 * Edge cases for retrying Kotlin `suspend` functions.
 */
class SuspendEdgeCaseTests {

    @Configuration
    @EnableReactiveRetry
    class Config {
        @Bean
        fun edgeService() = EdgeService()

        @Bean
        fun classLevelService() = ClassLevelService()

        @Bean
        fun retryFour(): MethodInterceptor = ReactiveRetryInterceptorBuilder.maxAttempts().setMaxAttempts(4).build()

        @Bean
        fun countingInterceptor() = CountingInterceptor()
    }

    class CountingInterceptor : MethodInterceptor {
        val invocations = AtomicInteger()

        override fun invoke(invocation: MethodInvocation): Any? {
            invocations.incrementAndGet()
            return invocation.proceed()
        }
    }

    @Service
    class EdgeService {
        val calls = AtomicInteger()
        val seenArgs: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val seenNames: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        val seenTraces: MutableList<String?> = Collections.synchronizedList(mutableListOf())
        val cancelled = AtomicBoolean()
        val perKey = ConcurrentHashMap<Int, AtomicInteger>()

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun withArgs(name: String, count: Int, tags: List<String>): String {
            seenArgs.add("$name/$count/$tags")
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return "$name-$count-${tags.joinToString()}"
        }

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun succeeds(): String {
            calls.incrementAndGet()
            return "ok"
        }

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun nullable(failures: Int): String? {
            if (calls.incrementAndGet() <= failures) throw IllegalStateException("not yet")
            return null
        }

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun unit(failures: Int) {
            if (calls.incrementAndGet() <= failures) throw IllegalStateException("not yet")
        }

        @ReactiveRetryable(
            maxAttempts = 3, exponentialBackoff = true, shouldCheckMaxInRow = true,
            backOffMinDelay = 1, backOffMaxDelay = 5,
        )
        suspend fun backOff(failures: Int): String {
            if (calls.incrementAndGet() <= failures) throw IllegalStateException("not yet")
            return "backed off"
        }

        @ReactiveRetryable(interceptor = "retryFour")
        suspend fun customRetryInterceptor(failures: Int): String {
            if (calls.incrementAndGet() <= failures) throw IllegalStateException("not yet")
            return "custom"
        }

        @ReactiveRetryable(interceptor = "countingInterceptor")
        suspend fun customPlainInterceptor(): String {
            calls.incrementAndGet()
            delay(1)
            return "plain"
        }

        @ReactiveRetryable(maxAttempts = Long.MAX_VALUE)
        suspend fun neverSucceeds(): String {
            calls.incrementAndGet()
            try {
                delay(20)
            } catch (e: CancellationException) {
                cancelled.set(true)
                throw e
            }
            throw IllegalStateException("never")
        }

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun context(): String {
            seenNames.add(currentCoroutineContext()[CoroutineName]?.name)
            seenTraces.add(TRACE.get())
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return "ctx"
        }

        @ReactiveRetryable(maxAttempts = 3)
        suspend fun flowFailingWhileCollected(): Flow<String> {
            val call = calls.incrementAndGet()
            return flow {
                emit("a")
                if (call < 3) throw IllegalStateException("collect $call")
                emit("b")
            }
        }

        @ReactiveRetryable(maxAttempts = 3, include = [IOException::class])
        suspend fun checked(): String {
            if (calls.incrementAndGet() < 3) throw IOException("io")
            return "io ok"
        }

        @ReactiveRetryable(maxAttempts = 2)
        suspend fun concurrent(key: Int): Int {
            delay(1)
            if (perKey.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet() < 3) {
                throw IllegalStateException("$key")
            }
            return key * 10
        }

        // Not suspend: the README says it is not retried.
        @ReactiveRetryable(maxAttempts = 3)
        fun plainFlow(): Flow<String> = flow {
            calls.incrementAndGet()
            throw IllegalStateException("plain flow")
        }
    }

    @ReactiveRetryable(maxAttempts = 2)
    @Service
    class ClassLevelService {
        val calls = AtomicInteger()

        suspend fun retried(): String {
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return "class"
        }

        fun notReactive(): String {
            calls.incrementAndGet()
            throw IllegalStateException("plain")
        }
    }

    private fun <T> withService(block: (EdgeService) -> T): T =
        AnnotationConfigApplicationContext(Config::class.java).use { block(it.getBean<EdgeService>()) }

    @Test
    fun `arguments are preserved across retries`() = withService { service ->
        val result = runBlocking { service.withArgs("x", 7, listOf("a", "b")) }
        assertEquals("x-7-a, b", result)
        assertEquals(List(3) { "x/7/[a, b]" }, service.seenArgs)
    }

    @Test
    fun `a suspending function that succeeds is called once`() = withService { service ->
        assertEquals("ok", runBlocking { service.succeeds() })
        assertEquals(1, service.calls.get())
    }

    @Test
    fun `a nullable result of null is returned as null`() = withService { service ->
        assertNull(runBlocking { service.nullable(0) })
        assertEquals(1, service.calls.get())
        assertNull(runBlocking { service.nullable(3) })
        assertEquals(4, service.calls.get())
    }

    @Test
    fun `a Unit suspending function is retried`() = withService { service ->
        runBlocking { service.unit(2) }
        assertEquals(3, service.calls.get())
    }

    @Test
    fun `exponential backoff with max in row retries a suspending function`() = withService { service ->
        assertEquals("backed off", runBlocking { service.backOff(3) })
        assertEquals(4, service.calls.get())
    }

    @Test
    fun `a custom retry interceptor bean retries a suspending function`() = withService { service ->
        assertEquals("custom", runBlocking { service.customRetryInterceptor(4) })
        assertEquals(5, service.calls.get())
    }

    @Test
    fun `a custom non-retrying interceptor bean passes the suspending call through`() =
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val service = context.getBean<EdgeService>()
            assertEquals("plain", runBlocking { service.customPlainInterceptor() })
            assertEquals(1, service.calls.get())
            assertEquals(1, context.getBean<CountingInterceptor>().invocations.get())
        }

    @Test
    fun `cancellation stops the retries`() = withService { service ->
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking { withTimeout(300) { service.neverSucceeds() } }
        }
        val attempts = service.calls.get()
        Thread.sleep(500)
        assertTrue(attempts >= 1, "attempts=$attempts")
        assertEquals(attempts, service.calls.get())
    }

    @Test
    fun `coroutine context elements are visible on every attempt`() = withService { service ->
        val result = runBlocking(CoroutineName("caller") + TraceElement("trace-1")) { service.context() }
        assertEquals("ctx", result)
        assertEquals(List(3) { "caller" }, service.seenNames)
        assertEquals(List(3) { "trace-1" }, service.seenTraces)
    }

    @Test
    fun `context elements are visible on retries from a different dispatcher`() = withService { service ->
        val result = runBlocking {
            withContext(Dispatchers.IO + CoroutineName("io-caller") + TraceElement("trace-io")) { service.context() }
        }
        assertEquals("ctx", result)
        assertEquals(List(3) { "io-caller" }, service.seenNames)
        assertEquals(List(3) { "trace-io" }, service.seenTraces)
    }

    @Test
    fun `a Flow that fails while collected is retried by calling the function again`() = withService { service ->
        val items = runBlocking { service.flowFailingWhileCollected().toList() }
        // Flux.retryWhen emits again the items emitted before each failure.
        assertEquals(listOf("a", "a", "a", "b"), items)
        assertEquals(3, service.calls.get())
    }

    @Test
    fun `an included checked exception is retried`() = withService { service ->
        assertEquals("io ok", runBlocking { service.checked() })
        assertEquals(3, service.calls.get())
    }

    @Test
    fun `concurrent suspending calls are retried independently`() = withService { service ->
        val results = runBlocking(Dispatchers.Default) {
            (1..50).map { key -> async { service.concurrent(key) } }.awaitAll()
        }
        assertEquals((1..50).map { it * 10 }, results)
        assertTrue(service.perKey.values.all { it.get() == 3 })
    }

    @Test
    fun `a non-suspending function returning a Flow is not retried`() = withService { service ->
        assertThrows(IllegalStateException::class.java) { runBlocking { service.plainFlow().toList() } }
        assertEquals(1, service.calls.get())
    }

    @Test
    fun `a class-level annotation retries suspending functions and passes other methods through`() =
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val service = context.getBean<ClassLevelService>()
            assertEquals("class", runBlocking { service.retried() })
            assertEquals(3, service.calls.get())
            assertThrows(IllegalStateException::class.java) { service.notReactive() }
            assertEquals(4, service.calls.get())
        }

    interface Greeter {
        suspend fun greet(name: String): String

        @ReactiveRetryable(maxAttempts = 2)
        suspend fun greetAnnotatedOnInterface(name: String): String

        suspend fun letters(): Flow<String>
    }

    class GreeterImpl : Greeter {
        val calls = AtomicInteger()

        @ReactiveRetryable(maxAttempts = 2)
        override suspend fun greet(name: String): String {
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return "Hi $name"
        }

        override suspend fun greetAnnotatedOnInterface(name: String): String {
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return "Hey $name"
        }

        @ReactiveRetryable(maxAttempts = 2)
        override suspend fun letters(): Flow<String> {
            if (calls.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return flow {
                emit("x")
                emit("y")
            }
        }
    }

    @Configuration
    @EnableReactiveRetry(proxyTargetClass = false)
    class JdkProxyConfig {
        @Bean
        fun greeter(): Greeter = GreeterImpl()
    }

    @Test
    fun `retries suspending functions through a JDK dynamic proxy`() =
        AnnotationConfigApplicationContext(JdkProxyConfig::class.java).use { context ->
            val greeter = context.getBean<Greeter>()
            assertTrue(AopUtils.isJdkDynamicProxy(greeter), greeter.javaClass.name)
            val target = (greeter as Advised).targetSource.target as GreeterImpl

            assertEquals("Hi Kotlin", runBlocking { greeter.greet("Kotlin") })
            assertEquals(3, target.calls.get())

            target.calls.set(0)
            assertEquals("Hey Kotlin", runBlocking { greeter.greetAnnotatedOnInterface("Kotlin") })
            assertEquals(3, target.calls.get())

            target.calls.set(0)
            assertEquals(listOf("x", "y"), runBlocking { greeter.letters().toList() })
            assertEquals(3, target.calls.get())
        }
}
