package nomisvai.secret;

public interface SecretRetriever {
    byte[] retrieveSecret(String secretId);
}
