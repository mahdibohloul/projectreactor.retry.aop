package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.aop.Advice;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Basic configuration for {@link ReactiveRetryable @ReactiveRetryable}
 * processing.
 *
 * @author: Mahdi Bohloul
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class ReactiveRetryConfiguration extends AbstractPointcutAdvisor
        implements
            IntroductionAdvisor,
            BeanFactoryAware,
            InitializingBean,
            SmartInitializingSingleton {

    private AnnotationAwareReactiveRetryOperationsInterceptor advice;

    private Pointcut pointcut;

    private BeanFactory beanFactory;

    @Override
    public ClassFilter getClassFilter() {
        return this.pointcut.getClassFilter();
    }

    @Override
    public void validateInterfaces() throws IllegalArgumentException {
    }

    @Override
    public Class<?>[] getInterfaces() {
        return new Class[]{io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryable.class};
    }

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterPropertiesSet() {
        Set<Class<? extends Annotation>> reactiveRetryableAnnotationTypes = new LinkedHashSet<>(1);
        reactiveRetryableAnnotationTypes.add(ReactiveRetryable.class);
        this.pointcut = buildPointcut(reactiveRetryableAnnotationTypes);
        this.advice = buildAdvice();
        ((BeanFactoryAware) this.advice).setBeanFactory(this.beanFactory);
    }

    @Override
    public void afterSingletonsInstantiated() {
    }

    private AnnotationAwareReactiveRetryOperationsInterceptor buildAdvice() {
        return new AnnotationAwareReactiveRetryOperationsInterceptor();
    }

    private Pointcut buildPointcut(Set<Class<? extends Annotation>> reactiveRetryableAnnotationTypes) {
        ComposablePointcut res = null;
        for (Class<? extends Annotation> annotationType : reactiveRetryableAnnotationTypes) {
            Pointcut filter = new AnnotationClassOrMethodPointcut(annotationType);
            if (res == null)
                res = new ComposablePointcut(filter);
            else
                res.union(filter);
        }
        return res;
    }

    private static final class AnnotationClassOrMethodPointcut extends StaticMethodMatcherPointcut {
        private final MethodMatcher methodMatcher;

        AnnotationClassOrMethodPointcut(Class<? extends Annotation> annotationType) {
            this.methodMatcher = new AnnotationMethodMatcher(annotationType);
            setClassFilter(new AnnotationClassOrMethodFilter(annotationType));
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return getClassFilter().matches(targetClass) || this.methodMatcher.matches(method, targetClass);
        }
    }

    private static final class AnnotationClassOrMethodFilter extends AnnotationClassFilter {
        private final AnnotationMethodResolver methodResolver;

        AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
            super(annotationType, true);
            this.methodResolver = new AnnotationMethodResolver(annotationType);
        }

        @Override
        public boolean matches(Class<?> clazz) {
            return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
        }
    }

    private static class AnnotationMethodResolver {

        private final Class<? extends Annotation> annotationType;

        public AnnotationMethodResolver(Class<? extends Annotation> annotationType) {
            this.annotationType = annotationType;
        }

        public boolean hasAnnotatedMethods(Class<?> clazz) {
            final AtomicBoolean found = new AtomicBoolean(false);
            ReflectionUtils.doWithMethods(clazz, method -> {
                if (found.get()) {
                    return;
                }
                Annotation annotation = AnnotationUtils.findAnnotation(method,
                        AnnotationMethodResolver.this.annotationType);
                if (annotation != null)
                    found.set(true);
            });
            return found.get();
        }
    }
}
