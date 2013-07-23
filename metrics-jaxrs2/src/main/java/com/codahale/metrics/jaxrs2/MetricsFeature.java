package com.codahale.metrics.jaxrs2;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.*;
import java.io.IOException;
import java.lang.reflect.Method;

import static com.codahale.metrics.MetricRegistry.name;

@Provider
public class MetricsFeature implements DynamicFeature {

    private final MetricRegistry registry;

    /**
     * Construct a resource method dispatch adapter using the given metrics registry name.
     *
     * @param registryName the name of a shared metric registry
     */
    public MetricsFeature(final String registryName) {
        this(SharedMetricRegistries.getOrCreate(registryName));
    }

    /**
     * Construct a resource method dispatch adapter using the given metrics registry.
     * <p/>
     *
     * @param registry a {@link MetricRegistry}
     */
    public MetricsFeature(final MetricRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {

        final Method method = resourceInfo.getResourceMethod();

        if (method.isAnnotationPresent(Timed.class)) {
            final Timed annotation = method.getAnnotation(Timed.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), method);
            final Timer timer = registry.timer(name);
            context.register(new TimedInterceptor(timer));
        }

        if (method.isAnnotationPresent(Metered.class)) {
            final Metered annotation = method.getAnnotation(Metered.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), method);
            final Meter meter = registry.meter(name);
            context.register(new MeteredInterceptor(meter));
        }

        if (method.isAnnotationPresent(ExceptionMetered.class)) {
            final ExceptionMetered annotation = method.getAnnotation(ExceptionMetered.class);
            final String name = chooseName(annotation.name(), annotation.absolute(), method, "exceptions");
            Class<? extends Throwable> cause = annotation.cause();
            final Meter meter = registry.meter(name);
            context.register(new ExceptionMeteredInterceptor(meter, cause));
        }
    }

    private static class TimedInterceptor implements WriterInterceptor {

        private final Timer timer;

        TimedInterceptor(final Timer timer) {
            this.timer = timer;
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            final Timer.Context timerContext = timer.time();
            try {
                context.proceed();
            } finally {
                timerContext.stop();
            }
        }
    }

    private static class MeteredInterceptor implements WriterInterceptor {

        private final Meter meter;

        MeteredInterceptor(final Meter meter) {
            this.meter = meter;
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            meter.mark();
            context.proceed();
        }
    }

    private static class ExceptionMeteredInterceptor implements ReaderInterceptor, WriterInterceptor {

        private final Meter meter;
        private final Class<? extends Throwable> cause;

        ExceptionMeteredInterceptor(final Meter meter, final Class<? extends Throwable> cause) {
            this.meter = meter;
            this.cause = cause;
        }

        @Override
        public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
            try {
                return context.proceed();
            } catch (IOException t) {
                if (cause.isInstance(t.getCause())) {
                    meter.mark();
                }
                throw t;
            } catch (WebApplicationException t) {
                if (cause.isInstance(t.getCause())) {
                    meter.mark();
                }
                throw t;
            }
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            try {
                context.proceed();
            } catch (Throwable t) {
                if (cause.isInstance(t)) {
                    meter.mark();
                }
            }
        }
    }

    private String chooseName(String explicitName, boolean absolute, Method method, String... suffixes) {
        if (explicitName != null && !explicitName.isEmpty()) {
            if (absolute) {
                return explicitName;
            }
            return name(method.getDeclaringClass(), explicitName);
        }
        return name(name(method.getDeclaringClass(), method.getName()), suffixes);
    }
}
//@ExceptionMetered
//@Metered
//@Timed
