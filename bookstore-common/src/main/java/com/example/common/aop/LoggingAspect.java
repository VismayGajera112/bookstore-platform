package com.example.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * The Step 1 logging/timing aspect, now shared by every service: one place captures a concern that
 * applies everywhere, and each service's business code stays free of instrumentation.
 */
@Aspect
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("within(com.example..controller..*) || within(com.example..service..*) "
            + "|| within(com.example..client..*)")
    public void applicationLayers() {
    }

    @Around("applicationLayers()")
    public Object logAndTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String target = "%s.%s".formatted(
                joinPoint.getSignature().getDeclaringType().getSimpleName(),
                joinPoint.getSignature().getName());

        log.info("-> {} args={}", target, Arrays.toString(joinPoint.getArgs()));
        long startedAt = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            log.info("<- {} completed in {} ms", target, elapsedMillis(startedAt));
            return result;
        } catch (Throwable ex) {
            log.warn("<- {} failed after {} ms with {}: {}",
                    target, elapsedMillis(startedAt), ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
