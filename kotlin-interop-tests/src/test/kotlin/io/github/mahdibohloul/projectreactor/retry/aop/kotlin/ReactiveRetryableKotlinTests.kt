package io.github.mahdibohloul.projectreactor.retry.aop.kotlin

import io.github.mahdibohloul.projectreactor.retry.aop.annotation.EnableReactiveRetry
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Uses the library from Kotlin, the way a Kotlin Spring application does.
 */
class ReactiveRetryableKotlinTests {

    @Configuration
    @EnableReactiveRetry
    class RetryConfiguration {
        @Bean
        fun greetingService() = GreetingService()
    }

    // Final by default in Kotlin: the kotlin-spring compiler plugin opens it
    // because of @Service, so that it can be proxied.
    @Service
    class GreetingService {
        val subscriptions = AtomicInteger()

        @ReactiveRetryable(maxAttempts = 2)
        fun greet(name: String): Mono<String> = Mono.defer {
            if (subscriptions.incrementAndGet() < 3) Mono.error(IllegalStateException("not yet"))
            else Mono.just("Hello, $name")
        }

        @ReactiveRetryable(maxAttempts = 2)
        fun letters(): Flux<String> = Flux.defer {
            if (subscriptions.incrementAndGet() < 3) Flux.error(IllegalStateException("not yet"))
            else Flux.just("a", "b", "c")
        }

        @ReactiveRetryable(maxAttempts = 2)
        suspend fun greetSuspending(name: String, failures: Int): String {
            delay(1)
            if (subscriptions.incrementAndGet() <= failures) throw IllegalStateException("not yet")
            return "Hello, $name"
        }

        @ReactiveRetryable(maxAttempts = 2, exclude = [IllegalArgumentException::class])
        suspend fun rejectSuspending(): String {
            subscriptions.incrementAndGet()
            throw IllegalArgumentException("invalid")
        }

        @ReactiveRetryable(maxAttempts = 2)
        suspend fun lettersSuspending(): Flow<String> {
            if (subscriptions.incrementAndGet() < 3) throw IllegalStateException("not yet")
            return flowOf("a", "b", "c")
        }
    }

    @Test
    fun `retries a Mono returned by a Kotlin bean`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            assertEquals(true, AopUtils.isAopProxy(service))
            StepVerifier.create(service.greet("Kotlin")).expectNext("Hello, Kotlin").verifyComplete()
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `retried Mono can be awaited from a coroutine`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            val greeting = runBlocking { service.greet("Kotlin").awaitSingle() }
            assertEquals("Hello, Kotlin", greeting)
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `retried Flux can be collected as a Flow`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            val letters = runBlocking { service.letters().asFlow().toList() }
            assertEquals(listOf("a", "b", "c"), letters)
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `retries a suspending function`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            val greeting = runBlocking { service.greetSuspending("Kotlin", failures = 2) }
            assertEquals("Hello, Kotlin", greeting)
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `throws the last error of a suspending function when the retries are exhausted`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking { service.greetSuspending("Kotlin", failures = 3) }
            }
            assertEquals("not yet", error.message)
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `does not retry an excluded error of a suspending function`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            assertThrows(IllegalArgumentException::class.java) { runBlocking { service.rejectSuspending() } }
            assertEquals(1, service.subscriptions.get())
        }
    }

    @Test
    fun `retries a suspending function that returns a Flow`() {
        AnnotationConfigApplicationContext(RetryConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            val letters = runBlocking { service.lettersSuspending().toList() }
            assertEquals(listOf("a", "b", "c"), letters)
            assertEquals(3, service.subscriptions.get())
        }
    }
}
