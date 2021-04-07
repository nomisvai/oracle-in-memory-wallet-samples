package nomisvai;

import io.dropwizard.Application;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.security.Security;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import nomisvai.configuration.SampleServiceConfiguration;
import nomisvai.resources.UserResource;
import nomisvai.secret.FileBasedSecretRetriever;
import nomisvai.secret.OciVaultSecretRetriever;
import nomisvai.secret.SecretRetriever;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

@Slf4j
public class SampleServiceApplication extends Application<SampleServiceConfiguration> {
    /** Service starts here */
    public static void main(final String[] args) throws Exception {
        new SampleServiceApplication().run(args);
    }

    @Override
    public String getName() {
        return "SampleService";
    }

    @Override
    public void initialize(final Bootstrap<SampleServiceConfiguration> bootstrap) {
        log.info("Initializing");
    }

    @Override
    public void run(final SampleServiceConfiguration configuration, final Environment environment) {
        // Add bouncy castle provider
        Security.addProvider(new BouncyCastleFipsProvider());

        Jdbi jdbi = bootstrapDb(configuration, environment);

        log.info("Registering User Resource");
        // Register the user resource class
        environment.jersey().register(new UserResource(jdbi));
    }

    /** Create a jdbi object from the drop wizard config */
    private Jdbi bootstrapDb(
            final SampleServiceConfiguration configuration, final Environment environment) {
        final JdbiFactory factory = new JdbiFactory();

        // Select the right secret retriever, this will be used to do substitution of secret ids
        // for some properties in SampleServiceConfiguration.InMemoryWalletDataSourceFactory
        SecretRetriever secretRetriever =
                configuration.getStage() == SampleServiceConfiguration.Stage.cloud
                        ? new OciVaultSecretRetriever()
                        : new FileBasedSecretRetriever();

        log.info("Initialized secret retriever: {}", secretRetriever.getClass().getName());

        log.info("Building managed DataSource");
        ManagedDataSource managedDataSource =
                configuration.getDatabase().build(environment.metrics(), "db", secretRetriever);

        applySchema(managedDataSource);

        log.info("Building Jdbi");
        final Jdbi jdbi =
                factory.build(environment, configuration.getDatabase(), managedDataSource, "db");

        log.info("Installing Jdbi plugin and registering custom mappers");
        // Install sql object and a mapper for timestamp to date
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.registerColumnMapper(
                new ColumnMapper<Date>() {
                    @Override
                    public Date map(ResultSet resultSet, int columnNumber, StatementContext ctx)
                            throws SQLException {
                        return resultSet.getTimestamp(columnNumber);
                    }
                });
        return jdbi;
    }

    private void applySchema(ManagedDataSource dataSource) {
        try {
            log.info("Database schema update check");
            java.sql.Connection connection = dataSource.getConnection();
            System.setProperty("liquibase.hub.mode", "off");

            Database database =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase =
                    new Liquibase(
                            "classpath:/db/changelog.xml",
                            new ClassLoaderResourceAccessor(),
                            database);
            liquibase.update(new Contexts());
        } catch (LiquibaseException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
