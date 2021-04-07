package nomisvai.secret;

import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.SneakyThrows;

/** This class is used when testing locally when the stage is set to d */
public class FileBasedSecretRetriever implements SecretRetriever {
    @Override
    @SneakyThrows
    public byte[] retrieveSecret(String secretId) {
        return Files.readAllBytes(Paths.get(secretId));
    }
}
