package org.foss.promoter.quarkus.repo.routes;

import java.io.File;

//import io.micrometer.core.instrument.MeterRegistry;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.component.micrometer.MicrometerConstants;
//import org.apache.camel.component.micrometer.routepolicy.MicrometerRoutePolicyFactory;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
//import org.foss.promoter.common.PrometheusRegistryUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessRepositoryRoute extends RouteBuilder {
    private static final org.jboss.logging.Logger LOG = Logger.getLogger(ProcessRepositoryRoute.class);

    @ConfigProperty(name = "data.dir")
    String dataDir;

    @Inject
    private ProducerTemplate producerTemplate;

    private void process(Exchange exchange) {
        String repo = exchange.getMessage().getBody(String.class);

        if (repo == null) {
            exchange.getMessage().setHeader("valid", false);
            return;
        }

        final String[] parts = repo.split("/");
        if (parts == null || parts.length == 0) {
            exchange.getMessage().setHeader("valid", false);
            return;
        }

        var name = parts[parts.length - 1];
        exchange.getMessage().setHeader("valid", true);
        exchange.getMessage().setHeader("name", name);

        File repoDir = new File(dataDir, name);
        exchange.getMessage().setHeader("exists", repoDir.exists() && repoDir.isDirectory());

        LOG.infof("Processing repository %s with address %s ", name, repo);
    }


    private void doSend(RevCommit rc) {
        LOG.debugf("Commit message: %s", rc.getShortMessage());

        producerTemplate.sendBody("direct:collected", rc.getShortMessage());
    }

    private void processLogEntry(Exchange exchange) {
        LOG.info("Processing log entries");
        RevWalk walk = exchange.getMessage().getBody(RevWalk.class);

        walk.forEach(this::doSend);
    }

    @Override
    public void configure() {
        // TODO: remove from here
//        MeterRegistry registry = PrometheusRegistryUtil.ppgetMetricRegistry();
//
//        getContext().getRegistry().bind(MicrometerConstants.METRICS_REGISTRY_NAME, registry);
//        getContext().addRoutePolicyFactory(new MicrometerRoutePolicyFactory());

        // Handles the request body
        from("kafka:repositories")
                .routeId("repositories")
                .process(this::process)
                .choice()
                .when(header("valid").isEqualTo(true))
                    .to("direct:valid")
                .otherwise()
                    .to("direct:invalid");

        // If it's a valid repo, then either clone of pull (depending on whether the dest dir exists)
        from("direct:valid")
                .routeId("repositories-valid")
                .choice()
                .when(header("exists").isEqualTo(false))
                    .to("direct:clone")
                .otherwise()
                    .to("direct:pull")
                .end()
                .to("direct:log");

        from("direct:clone")
                .routeId("repositories-clone")
                .toD(String.format("git://%s/${header.name}?operation=clone&remotePath=${body}", dataDir));

        from("direct:pull")
                .routeId("repositories-pull")
                .toD(String.format("git://%s/${header.name}?operation=pull&remoteName=origin", dataDir));

        // Logs if invalid stuff is provided
        from("direct:invalid")
                .routeId("repositories-invalid")
                .log(LoggingLevel.ERROR, "Unable to process repository ${body}");

        // Handles each commit on the repository
        from("direct:log")
                .routeId("repositories-log")
                .toD(String.format("git://%s/${header.name}?operation=log", dataDir))
                .process(this::processLogEntry)
                .to("direct:collected");

        from("direct:collected")
                .routeId("repositories-collected")
                // For the demo: this one is really cool, because without it, sending to Kafka is quite slow
//                .aggregate(constant(true)).completionSize(20).aggregationStrategy(AggregationStrategies.groupedBody())
//                .threads(5)
                .toF("kafka:commits");
    }


}
