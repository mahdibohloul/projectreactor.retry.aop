package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;

/**
 * A static utility class that provides methods the clone of the method
 * invocation. A note about the cloning of the method invocation: The method
 * invocation is cloned to avoid the side effects of the retry mechanism. By
 * default, if the method invocation is not cloned, the retry mechanism will
 * retry the original method invocation and not the cloned one. So in the
 * retrying process the another method interceptors cannot be invoked.
 *
 * @author Mahdi Bohloul
 * @since 1.4.0
 */
public class ReactiveRetryUtil {
	private ReactiveRetryUtil() {
	}

	/**
	 * Clone the given method invocation.
	 *
	 * @param invocation
	 *            the method invocation to be cloned it should be an instance of
	 *            {@link ProxyMethodInvocation}
	 * @return the cloned method invocation
	 * @author Mahdi Bohloul
	 * @since 1.4.0
	 */
	public static MethodInvocation invocableClone(MethodInvocation invocation) {
		if (invocation instanceof ProxyMethodInvocation)
			return ((ProxyMethodInvocation) invocation).invocableClone();
		return invocation;
	}
}
