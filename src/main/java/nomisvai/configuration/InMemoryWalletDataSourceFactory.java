package nomisvai.configuration;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.db.ManagedPooledDataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import nomisvai.secret.SecretRetriever;
import oracle.jdbc.pool.OracleDataSource;

@Getter
@Setter
/**
 * This class extends DataSourceFactory by adding keyStoreBase64/keyStorePassword trustStoreBase64
 * to the configuration of a database data source.
 *
 * <p>If The values of password, keyStoreBase64, keyStorePassword and trustStoreBase64 are prefixed
 * with {SECRET}, the rest of the value will be treated as a secret Id and the whole value will be
 * replaced with the retrieved secret.
 *
 * <p>It also overrides the build() method so that the underlying connection pool uses
 * OracleDataSource to create new db connections. The OracleDataSource object used is initialized
 * with a SSL context making use of the in-memory wallet.
 */
public class InMemoryWalletDataSourceFactory extends DataSourceFactory {
    private final String SECRET_PREFIX_TOKEN = "{SECRET}";
    private String keyStoreBase64;
    private String keyStorePassword;
    private String trustStoreBase64;

    /** Decoded keyStoreBase64 keystore */
    public byte[] getKeyStore() {
        return keyStoreBase64 == null || keyStoreBase64.isEmpty()
                ? null
                : Base64.getDecoder().decode(keyStoreBase64.getBytes(StandardCharsets.UTF_8));
    }

    /** Decoded trustStoreBase64 keystore */
    public byte[] getTrustStore() {
        return trustStoreBase64 == null || trustStoreBase64.isEmpty()
                ? null
                : Base64.getDecoder().decode(trustStoreBase64.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ManagedDataSource build(MetricRegistry metricRegistry, String name) {
        return build(metricRegistry, name, null);
    }

    private String resolveSecret(String value, SecretRetriever secretRetriever) {
        if (value == null || !value.startsWith(SECRET_PREFIX_TOKEN)) {
            return value;
        }
        String secretId = value.substring(SECRET_PREFIX_TOKEN.length());
        return new String(secretRetriever.retrieveSecret(secretId), StandardCharsets.UTF_8);
    }

    @SneakyThrows
    public ManagedDataSource build(
            MetricRegistry metricRegistry, String name, SecretRetriever secretRetriever) {

        if (secretRetriever == null) {
            throw new RuntimeException("Secret retriever must be provided");
        }

        // Substitute values prefixed with {SECRET} with their corresponding
        // values in the vault
        setPassword(resolveSecret(getPassword(), secretRetriever));
        setKeyStoreBase64(resolveSecret(getKeyStoreBase64(), secretRetriever));
        setKeyStorePassword(resolveSecret(getKeyStorePassword(), secretRetriever));
        setTrustStoreBase64(resolveSecret(getTrustStoreBase64(), secretRetriever));

        // Call the super method to build the pool
        ManagedDataSource managedDataSource = super.build(metricRegistry, name);

        if (!(managedDataSource instanceof ManagedPooledDataSource)) {
            throw new RuntimeException(
                    "Expected ManagedPoolDataSource instance but got "
                            + managedDataSource.getClass().getName());
        }

        ManagedPooledDataSource managedPooledDataSource =
                (ManagedPooledDataSource) managedDataSource;

        // Set the oracle datasource with the proper SSL context
        if (getUrl().startsWith("jdbc:oracle")) {
            OracleDataSource dataSource = new OracleDataSource();
            dataSource.setDataSourceName("sampleAppDataSource");
            dataSource.setUser(getUser());
            dataSource.setPassword(getPassword());
            dataSource.setURL(getUrl());
            dataSource.setConnectionProperties(managedPooledDataSource.getDbProperties());
            SSLContext sslContext = buildSSLContext();
            if (sslContext != null) {
                dataSource.setSSLContext(sslContext);
            }
            // Setting the data source will override the URL and use this instead
            managedPooledDataSource.setDataSource(dataSource);
        }

        return managedPooledDataSource;
    }

    /** Creates a SSLContext with a BCFKS keystore and truststore retrieved from the OCI Vault. */
    private SSLContext buildSSLContext() {
        byte[] keyStoreContent = getKeyStore();

        if (keyStoreContent != null) {
            try (InputStream keyStoreStream = new ByteArrayInputStream(keyStoreContent);
                    InputStream trustStoreStream = new ByteArrayInputStream(getTrustStore())) {
                KeyStore keyStore = KeyStore.getInstance("BCFKS");
                keyStore.load(
                        keyStoreStream,
                        getKeyStorePassword() == null ? null : getKeyStorePassword().toCharArray());

                KeyStore trustStore = KeyStore.getInstance("BCFKS");
                trustStore.load(
                        trustStoreStream,
                        getKeyStorePassword() == null ? null : getKeyStorePassword().toCharArray());

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");

                trustManagerFactory.init(trustStore);
                keyManagerFactory.init(keyStore, getKeyStorePassword().toCharArray());

                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(
                        keyManagerFactory.getKeyManagers(),
                        trustManagerFactory.getTrustManagers(),
                        null);
                return sslContext;
            } catch (IOException
                    | KeyStoreException
                    | UnrecoverableKeyException
                    | CertificateException
                    | NoSuchAlgorithmException
                    | KeyManagementException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
