package io.github.mahdibohloul.projectreactor.retry.aop;

import io.github.mahdibohloul.projectreactor.retry.aop.annotation.EnableReactiveRetry;
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable;
import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryInterceptorBuilder;
import java.util.Properties;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import reactor.core.publisher.Mono;

public class ApplicationTests {
    @Configuration
    @EnableReactiveRetry(order = 1)
    public static class TestConfiguration {
        @Bean
        public static PropertySourcesPlaceholderConfigurer pspc() {
            PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
            Properties properties = new Properties();
            properties.setProperty("one", "1");
            properties.setProperty("five", "5");
            properties.setProperty("onePointOne", "1.1");
            properties.setProperty("retryMethod", "shouldRetry");
            pspc.setProperties(properties);
            return pspc;
        }

        @Bean
        public Service service() {
            return new Service();
        }

        @Bean
        public ExcludesService excludesService() {
            return new ExcludesService();
        }

        @Bean
        public RetryableService retryableService() {
            return new RetryableService();
        }

        @Bean
        public MethodInterceptor retryInterceptor() {
            return ReactiveRetryInterceptorBuilder.maxAttempts().setMaxAttempts(5).build();
        }

        @Bean
        public CustomInterceptorService customInterceptorService() {
            return new CustomInterceptorService();
        }
    }

    @Configuration
    public static class WithoutRetryConfiguration {
        @Bean
        public Service service() {
            return new Service();
        }
    }

    @Configuration
    @EnableReactiveRetry(proxyTargetClass = true)
    public static class TestProxyConfiguration {
        @Bean
        public Service service() {
            return new Service();
        }
    }

    public static class Service {
        private int count = 0;

        @ReactiveRetryable
        public Mono<Void> service() {
            return Mono.defer(() -> {
                if (this.count++ < 2)
                    return Mono.error(new RuntimeException("error"));
                return Mono.empty();
            });
        }

        @ReactiveRetryable(maxAttempts = 1)
        public Mono<Void> exhaustedRetry() {
            return Mono.defer(() -> {
                if (this.count++ < 2)
                    return Mono.error(new RuntimeException("error"));
                return Mono.empty();
            });
        }

        public int getCount() {
            return count;
        }
    }

    public static class ExcludesService {
        private int count = 0;

        @ReactiveRetryable(exclude = {IllegalStateException.class})
        public Mono<Void> service() {
            return Mono.defer(() -> {
                if (this.count++ < 2)
                    return Mono.error(new IllegalStateException("error"));
                return Mono.empty();
            });
        }

        public int getCount() {
            return count;
        }
    }

    @ReactiveRetryable
    public static class RetryableService {
        private int count = 0;

        public Mono<Void> service() {
            return Mono.defer(() -> {
                if (this.count++ < 2)
                    return Mono.error(new RuntimeException("error"));
                return Mono.empty();
            });
        }

        public int getCount() {
            return count;
        }
    }

    public static class CustomInterceptorService {
        private int count = 0;

        @ReactiveRetryable(interceptor = "retryInterceptor")
        public Mono<Void> service() {
            return Mono.defer(() -> {
                if (this.count++ < 7)
                    return Mono.error(new RuntimeException("error"));
                return Mono.empty();
            });
        }

        public int getCount() {
            return count;
        }
    }
}
