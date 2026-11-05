package ph.thecoffeejunkie.crm.shared;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.EventListener;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * DEBUG entry/exit for every service and component call in the app, WARN for calls of 500 ms or more at any level,
 * and job IDs for listeners and scheduled tasks (spec 2026-11-05 logging). Spring AOP proxies; no AspectJ.
 */
@Configuration(proxyBeanMethods = false)
class ServiceCallLogging {

    static final long SLOW_MS = 500;
    private static final String ROOT = "ph.thecoffeejunkie.crm.";
    private static final Set<Class<?>> SKIP = Set.of(ServiceCallLogging.class, LogRedactor.class, JobMdc.class,
            CurrentActor.class, AccessLogFilter.class, RequestIdFilter.class);
    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor serviceCallAdvisor() {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut(), (MethodInterceptor) ServiceCallLogging::invoke);
        // Inside the transaction advice, so the timing covers only the method.
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE - 1);
        return advisor;
    }

    /** Scheduled tasks are run by the scheduler, possibly on the raw bean: decorate there so they always get IDs. */
    @Bean
    static BeanPostProcessor jobMdcSchedulerDecorator() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof ThreadPoolTaskScheduler s) {
                    s.setTaskDecorator(JobMdc::decorate);
                } else if (bean instanceof SimpleAsyncTaskScheduler s) {
                    s.setTaskDecorator(JobMdc::decorate);
                }
                return bean;
            }
        };
    }

    static boolean candidate(Class<?> type) {
        return type.getName().startsWith(ROOT)
                && !Modifier.isFinal(type.getModifiers()) && !type.isInterface() && !type.isRecord()
                && !SKIP.contains(type)
                && !jakarta.servlet.Filter.class.isAssignableFrom(type)
                && !"ph.thecoffeejunkie.crm.audit.AuditLog".equals(type.getName())
                && AnnotatedElementUtils.hasAnnotation(type, Component.class)
                && !AnnotatedElementUtils.hasAnnotation(type, Configuration.class)
                && !AnnotatedElementUtils.hasAnnotation(type, Controller.class);
    }

    private static Pointcut pointcut() {
        ClassFilter classes = ServiceCallLogging::candidate;
        MethodMatcher methods = new StaticMethodMatcher() {
            @Override
            public boolean matches(Method m, Class<?> target) {
                return m.getDeclaringClass() != Object.class && !m.isSynthetic() && !m.isBridge()
                        && !Modifier.isStatic(m.getModifiers()) && !Modifier.isPrivate(m.getModifiers());
            }
        };
        return new Pointcut() {
            @Override
            public ClassFilter getClassFilter() {
                return classes;
            }

            @Override
            public MethodMatcher getMethodMatcher() {
                return methods;
            }
        };
    }

    private static Object invoke(MethodInvocation inv) throws Throwable {
        Object target = inv.getThis();
        Class<?> type = target == null ? inv.getMethod().getDeclaringClass() : AopUtils.getTargetClass(target);
        Method method = AopUtils.getMostSpecificMethod(inv.getMethod(), type);
        if (isJob(method)) {
            String job = type.getSimpleName() + "." + method.getName();
            Throwable[] thrown = new Throwable[1];
            Object result = JobMdc.call(job, () -> {
                try {
                    return logged(inv, type, method);
                } catch (Throwable t) {
                    thrown[0] = t;
                    return null;
                }
            });
            if (thrown[0] != null) {
                throw thrown[0];
            }
            return result;
        }
        return logged(inv, type, method);
    }

    private static boolean isJob(Method m) {
        return AnnotatedElementUtils.hasAnnotation(m, Scheduled.class)
                || AnnotatedElementUtils.hasAnnotation(m, EventListener.class)
                || AnnotatedElementUtils.hasAnnotation(m, TransactionalEventListener.class);
    }

    private static Object logged(MethodInvocation inv, Class<?> type, Method method) throws Throwable {
        Logger log = LoggerFactory.getLogger(type);
        String name = method.getName();
        if (log.isDebugEnabled()) {
            log.debug("→ {}({})", name, LogRedactor.renderArgs(NAMES.getParameterNames(method), inv.getArguments()));
        }
        long start = System.nanoTime();
        try {
            Object result = inv.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms >= SLOW_MS) {
                log.warn("slow → {} in {}ms", name, ms);
            } else {
                log.debug("← {} in {}ms", name, ms);
            }
            return result;
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                log.debug("✗ {} threw {}: {}", name, t.getClass().getSimpleName(), t.getMessage());
            }
            throw t;
        }
    }
}
