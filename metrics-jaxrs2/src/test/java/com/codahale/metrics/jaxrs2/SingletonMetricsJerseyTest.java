package com.codahale.metrics.jaxrs2;

import com.codahale.metrics.jaxrs2.resources.InstrumentedResource;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.codahale.metrics.MetricRegistry.name;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.fest.assertions.api.Assertions.failBecauseExceptionWasNotThrown;

/**
 * Tests importing {@link com.codahale.metrics.jaxrs2.MetricsFeature} as a singleton
 * in a Jersey {@link org.glassfish.jersey.server.ResourceConfig}
 */
public class SingletonMetricsJerseyTest extends JerseyTest {
    static {
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.OFF);
        Logger.getLogger("org.glassfish.grizzly").setLevel(Level.OFF);
    }

    private MetricRegistry registry;

    @Override
    protected Application configure() {
        this.registry = new MetricRegistry();

        final ResourceConfig config = new ResourceConfig();
        config.register(new MetricsFeature(registry));
        config.register(InstrumentedResource.class);

        return config.getApplication();
    }

    @Test
    public void timedMethodsAreTimed() {
        assertThat(target("timed").request().get(String.class)).isEqualTo("yay");
        final Timer timer = registry.timer(name(InstrumentedResource.class, "timed"));
        assertThat(timer.getCount()).isEqualTo(1);
    }

    @Test
    public void meteredMethodsAreMetered() {
        assertThat(target("metered").request().get(String.class)).isEqualTo("woo");
        final Meter meter = registry.meter(name(InstrumentedResource.class, "metered"));
        assertThat(meter.getCount()).isEqualTo(1);
    }

    @Test
    public void exceptionMeteredMethodsAreExceptionMetered() {
        final Meter meter = registry.meter(name(InstrumentedResource.class,
                                                "exceptionMetered",
                                                "exceptions"));

        assertThat(target("exception-metered").request().get(String.class)).isEqualTo("fuh");

        assertThat(meter.getCount()).isZero();

        Throwable resultException = null;

        try {
            target("exception-metered").queryParam("splode", "true").request().get(String.class);
            failBecauseExceptionWasNotThrown(Exception.class);
        } catch (ProcessingException e) {
            resultException = e.getCause().getCause();
        } catch (WebApplicationException e) {
            resultException = e;
        } catch (Exception e) {
            resultException = e;
        }

        assertThat(resultException).isNotNull();
        assertThat(meter.getCount()).isEqualTo(1);
    }
}
