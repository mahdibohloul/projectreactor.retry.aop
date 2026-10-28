package io.github.mahdibohloul.projectreactor.retry.aop.kotlin

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
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

/**
 * Checks the README claims about Spring's `@Retryable` that the comparison
 * tests do not cover.
 */
class SpringRetryableFlowTests {

    @Configuration
    @EnableResilientMethods
    class Config {
        @Bean
        fun flowService() = FlowService()
    }

    @Service
    class FlowService {
        val invocations = AtomicInteger()
        val collections = AtomicInteger()

        @Retryable(maxRetries = 2, delay = 0)
        fun letters(): Flow<String> {
            invocations.incrementAndGet()
            return flow {
                if (collections.incrementAndGet() <= 2) throw IllegalStateException("not yet")
                emit("a")
            }
        }

        @Retryable(maxRetries = 2, delay = 0)
        fun alwaysFails(): Flow<String> = flow {
            collections.incrementAndGet()
            throw IllegalStateException("always")
        }

        @Retryable(maxRetries = 2, delay = 0)
        fun throwsBeforeReturning(): Mono<String> {
            invocations.incrementAndGet()
            throw IllegalStateException("sync")
        }
    }

    @Test
    fun `Spring retries a Kotlin Flow return value by collecting it again`() =
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val service = context.getBean<FlowService>()
            val letters = runBlocking { service.letters().toList() }
            assertEquals(listOf("a"), letters)
            assertEquals(1, service.invocations.get())
            assertEquals(3, service.collections.get())
        }

    @Test
    fun `Spring does not retry an exception thrown before the publisher is returned`() =
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val service = context.getBean<FlowService>()
            assertThrows(IllegalStateException::class.java) { service.throwsBeforeReturning() }
            assertEquals(1, service.invocations.get())
        }

    @Test
    fun `Spring wraps the last error of a reactive method in RetryExhaustedException`() =
        AnnotationConfigApplicationContext(Config::class.java).use { context ->
            val service = context.getBean<FlowService>()
            val error = assertThrows(Throwable::class.java) { runBlocking { service.alwaysFails().toList() } }
            assertEquals(true, Exceptions.isRetryExhausted(error))
            assertEquals(3, service.collections.get())
        }
}
