package nomisvai.secret;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import java.util.Base64;

/**
 * This class is used when the stage is set to cloud, it retrieves secrets from the vault.
 *
 * <p>The service using this must be deployed in an OCI instance that has the proper grants to read
 * the secret. see:
 * https://docs.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm
 */
public class OciVaultSecretRetriever implements SecretRetriever {
    private final SecretsClient secretsClient;

    public OciVaultSecretRetriever() {
        secretsClient =
                SecretsClient.builder()
                        .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
    }

    @Override
    public byte[] retrieveSecret(String secretId) {
        Base64SecretBundleContentDetails contentDetails =
                (Base64SecretBundleContentDetails)
                        secretsClient
                                .getSecretBundle(
                                        GetSecretBundleRequest.builder()
                                                .secretId(secretId)
                                                .stage(GetSecretBundleRequest.Stage.Current)
                                                .build())
                                .getSecretBundle()
                                .getSecretBundleContent();
        return Base64.getDecoder().decode(contentDetails.getContent());
    }
}
