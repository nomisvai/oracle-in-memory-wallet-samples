package nomisvai;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Security;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;
import oracle.security.pki.OraclePKIProvider;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** These tests expect the */
class SampleServiceApplicationTest {
    private static String dbUrl;
    private static boolean initFailed = true;
    private static String keyStorePassword;
    private static String userName;
    private static String userPassword;
    private static String vaultDir = System.getProperty("fakevaultdir");
    private static String walletDir = System.getProperty("walletdir");

    @BeforeAll
    public static void setup() {
        vaultDir = vaultDir.endsWith(File.separator) ? vaultDir : vaultDir + File.separator;
        walletDir = walletDir.endsWith(File.separator) ? walletDir : walletDir + File.separator;
        Security.addProvider(new BouncyCastleFipsProvider());
        userName = readString(vaultDir + "user.name");
        userPassword = readString(vaultDir + "user.password");
        keyStorePassword = readString(vaultDir + "keystore.password");
        dbUrl = readString(walletDir + "tnsnames.ora");
        // Extract the first line and use as the connect string
        dbUrl =
                "jdbc:oracle:thin:@"
                        + dbUrl.substring(dbUrl.indexOf("=") + 1, dbUrl.indexOf("\n")).trim();

        initFailed =
                Stream.of(userName, userPassword, keyStorePassword, dbUrl)
                                .filter(Objects::isNull)
                                .count()
                        > 0;
    }

    @SneakyThrows
    public static String readString(String fileName) {
        byte[] content = readBytes(fileName);
        return content == null ? null : new String(content, StandardCharsets.UTF_8);
    }

    @SneakyThrows
    public static byte[] readBytes(String fileName) {
        try {
            return Files.readAllBytes(Paths.get(fileName));
        } catch (RuntimeException e) {
            System.out.println("Cannot read " + fileName + ", " + e);
            return null;
        }
    }

    private static void jdbiTest(SSLContext sslContext) throws Exception {
        long start = System.currentTimeMillis();
        try {
            Properties info = new Properties();

            info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, userName);
            info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, userPassword);

            OracleDataSource ods = new OracleDataSource();
            ods.setSSLContext(sslContext);
            ods.setURL(dbUrl);
            ods.setConnectionProperties(info);
            Jdbi jdbi = Jdbi.create(ods);
            jdbi.withHandle(
                    handle -> {
                        return handle.createQuery("SELECT 1 FROM dual").mapTo(String.class).first();
                    });
        } finally {
            System.out.println("Elapsed " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    @SneakyThrows
    static SSLContext createSSLContextJks(
            InputStream keyStoreStream, InputStream trustStoreStream) {
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(trustStoreStream, keyStorePassword.toCharArray());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(
                keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @SneakyThrows
    static SSLContext createSSLContextBcfks(
            InputStream keyStoreStream, InputStream trustStoreStream) {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");

        KeyStore keyStore = KeyStore.getInstance("BCFKS");
        keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        KeyStore trustStore = KeyStore.getInstance("BCFKS");
        trustStore.load(trustStoreStream, keyStorePassword.toCharArray());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(
                keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @SneakyThrows
    static SSLContext createSSLContextSso(InputStream keyStoreStream) {
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        KeyStore keyStore = KeyStore.getInstance("SSO", new OraclePKIProvider());
        keyStore.load(keyStoreStream, null);
        keyManagerFactory.init(keyStore, null);
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(
                keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @SneakyThrows
    static SSLContext createSSLContextP12(InputStream keyStoreStream) {
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        KeyStore keyStore = KeyStore.getInstance("PKCS12", new OraclePKIProvider());
        keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(
                keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    @Test
    public void jksStoreTest() throws Exception {
        byte[] keyStoreJks = readBytes(walletDir + "keystore.jks");
        byte[] trustStoreJks = readBytes(walletDir + "truststore.jks");
        if (initFailed || keyStoreJks == null || trustStoreJks == null) {
            System.out.println("jksStoreTest disabled, missing info");
            return;
        }

        jdbiTest(
                createSSLContextJks(
                        new ByteArrayInputStream(keyStoreJks),
                        new ByteArrayInputStream(trustStoreJks)));
        System.out.println("Connected successfully using JKS");
    }

    @Test
    public void cwalletSsoTest() throws Exception {
        byte[] keyStore = readBytes(walletDir + "cwallet.sso");

        if (initFailed || keyStore == null) {
            System.out.println("cwalletSsoTest disabled, missing info");
            return;
        }

        jdbiTest(createSSLContextSso(new ByteArrayInputStream(keyStore)));
        System.out.println("Connected successfully using cwallet.sso");
    }

    @Test
    public void bcfksTest() throws Exception {
        byte[] keyStore = readBytes(walletDir + "keystore.bcfks");
        byte[] trustStore = readBytes(walletDir + "truststore.bcfks");
        if (initFailed || keyStore == null || trustStore == null) {
            System.out.println("bcfksTest disabled, missing info");
            return;
        }

        jdbiTest(
                createSSLContextBcfks(
                        new ByteArrayInputStream(keyStore), new ByteArrayInputStream(trustStore)));
        System.out.println("Connected successfully using BCFKS");
    }

    @Test
    public void p12Test() throws Exception {
        byte[] keyStore = readBytes(walletDir + "ewallet.p12");
        if (initFailed || keyStore == null) {
            System.out.println("p12Test disabled, missing info");
            return;
        }

        jdbiTest(createSSLContextP12(new ByteArrayInputStream(keyStore)));
        System.out.println("Connected successfully using P12");
    }
}
