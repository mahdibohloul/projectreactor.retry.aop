package io.github.mahdibohloul.projectreactor.retry.aop.kotlin

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Pins down how Spring Framework 7's own `@Retryable` behaves on reactive
 * methods. The comparison with this library in the README relies on each of
 * these behaviors.
 */
class SpringRetryableComparisonTests {

    @Configuration
    @EnableResilientMethods
    class ResilienceConfiguration {
        @Bean
        fun greetingService() = GreetingService()
    }

    @Service
    class GreetingService {
        val invocations = AtomicInteger()
        val subscriptions = AtomicInteger()

        // Same as @ReactiveRetryable(maxAttempts = 2): Spring waits 1 second
        // between retries by default, this library does not wait.
        @Retryable(maxRetries = 2, delay = 0)
        fun greet(failures: Int): Mono<String> {
            invocations.incrementAndGet()
            return Mono.defer {
                if (subscriptions.incrementAndGet() <= failures) Mono.error(IllegalStateException("not yet"))
                else Mono.just("Hello")
            }
        }

        @Retryable(excludes = [IllegalArgumentException::class], maxRetries = 2, delay = 0)
        fun reject(): Mono<String> = Mono.defer {
            subscriptions.incrementAndGet()
            Mono.error(IllegalArgumentException("invalid"))
        }

        @Retryable(maxRetries = 2, delay = 0)
        suspend fun greetSuspending(): String {
            if (invocations.incrementAndGet() <= 2) throw IllegalStateException("not yet")
            return "Hello"
        }
    }

    @Test
    fun `retries a Mono by subscribing again, without calling the method again`() {
        AnnotationConfigApplicationContext(ResilienceConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            StepVerifier.create(service.greet(failures = 2)).expectNext("Hello").verifyComplete()
            assertEquals(3, service.subscriptions.get())
            // This library calls the method again on each retry instead.
            assertEquals(1, service.invocations.get())
        }
    }

    @Test
    fun `wraps the last error when the retries are exhausted`() {
        AnnotationConfigApplicationContext(ResilienceConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            // This library signals the last error itself instead.
            StepVerifier.create(service.greet(failures = 3))
                .expectErrorMatches { Exceptions.isRetryExhausted(it) && it.cause is IllegalStateException }
                .verify()
            assertEquals(3, service.subscriptions.get())
        }
    }

    @Test
    fun `does not retry excluded errors`() {
        AnnotationConfigApplicationContext(ResilienceConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            StepVerifier.create(service.reject()).expectError(IllegalArgumentException::class.java).verify()
            assertEquals(1, service.subscriptions.get())
        }
    }

    @Test
    fun `does not retry suspending functions`() {
        AnnotationConfigApplicationContext(ResilienceConfiguration::class.java).use { context ->
            val service = context.getBean<GreetingService>()
            // This library retries them.
            assertThrows(IllegalStateException::class.java) { runBlocking { service.greetSuspending() } }
            assertEquals(1, service.invocations.get())
        }
    }
}
