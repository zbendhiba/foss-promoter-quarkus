package org.foss.promoter.quarkus.commit.routes;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.foss.promoter.quarkus.commit.common.CassandraClient;
import org.foss.promoter.quarkus.commit.common.ContributionsDao;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CommitRoute extends RouteBuilder {
    private static final Logger LOG = Logger.getLogger(CommitRoute.class);

    @ConfigProperty(name="CASSANDRA_SERVER", defaultValue = "cassandra")
    String cassandraServer;

    @ConfigProperty(name="CASSANDRA_PORT", defaultValue = "9042")
    int cassandraPort;

    @ConfigProperty(name="IMAGE_SIZE", defaultValue = "1024")
    int imageSize;

    private final int consumersCount = ConfigProvider.getConfig().getValue("camel.component.kafka.consumers-count", int.class);

    /**
     * This creates the database, sets keyspace, etc so we don't need to create them manually before running the demo
     */
    private void createDatabase() {
        LOG.infof("Connecting to Cassandra server %s:%d", cassandraServer, cassandraPort);
        CassandraClient cassandraClient = new CassandraClient(cassandraServer, cassandraPort);
        final ContributionsDao contributionsDao = cassandraClient.newExampleDao();

        contributionsDao.createKeySpace();
        contributionsDao.useKeySpace();
        contributionsDao.createTable();
    }

    private void process(Exchange exchange) {


        String message = exchange.getMessage().getBody(String.class);
        LOG.debugf("Generating for: {}", message);

        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            bitMatrix = barcodeWriter.encode(message, BarcodeFormat.QR_CODE, imageSize, imageSize);
            final BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            LOG.trace("Writing data");
            ImageIO.write(bufferedImage, "png", bos);
            LOG.trace("Done!");

            exchange.getMessage().setBody(Arrays.asList(message, ByteBuffer.wrap(bos.toByteArray())));
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void configure() {
        // TODO: check if there's a Quarkus way of doing this
        createDatabase();

//        OpenTelemetryTracer ottracer = new OpenTelemetryTracer();
//        ottracer.init(getCamelContext());

        fromF("kafka:commits?groupId=fp-commit-service")
                .routeId("commit-qr")
// TODO: see the note on consumersCount property
                .threads(3)
                .process(this::process)
                .toF("cql://%s:%d/%s?cql=%s", cassandraServer, cassandraPort, ContributionsDao.KEY_SPACE,
                        ContributionsDao.getInsertStatement());

    }
}
