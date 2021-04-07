# Sample Service using in-memory wallets to connect to an Oracle Cloud Autonomous Database

What is this?
==
This is a small sample service demonstrating the use of in-memory wallets retrieved live from the
OCI Vault. All components required by this service are included in the Oracle free
tier: https://www.oracle.com/cloud/free/

Here is a summary of what it contains:

* A simple rest service built using Dropwizard
* Augmented Dropwizard
  DataSourceFactory (https://www.dropwizard.io/en/latest/manual/configuration.html#database) to
  support in-memory wallet
* DB access done with JDBI using SQL Object API
* Schema creation using Liquibase
* Supports BouncyCastle keystore (BCFKS) wallet
* Script provided to convert JKS keystore to BCFKS keystore
* See unit tests for samples using in-memory wallets of different formats (jks, cwallet.sso,
  ewallet.p12)
* Secrets downloaded from the OCI Vault by the service using instance principal authentication.

Running on your desktop
==

Wallets and secrets
--
The local setup will connect to your autonomous database but will use a local file based vault to
simulate the OCI Vault, see FileBasedSecretRetriever.java. It simply reads files containing secrets
from your local disk.

* From the root of the repo, create a wallet/ directory and a fakevault/ directory.
* Download your ADB-S wallet and unzip it in wallet/
* Execute the provided script to create the bcfks wallets "scripts/convertWallet.sh <wallet_dir> <
  src_password> <dst_password>". Use the same passwords for the src and dst wallets (Required for
  the tests to work):

```
  # Note: this script will download the bcfips jars in $HOME/jar (if they are not already there)
  # and the java keytool will use them to convert the jks store to bcfks format.
  # It will create wallet/keystore.bcfks and wallet/truststore.bcfks 
  scripts/convertWallet.sh wallet Welcome1 Welcome1
```

* create a fake vault containing your secrets (use the same filename as below, as they are hardcoded
  in the unit tests:

```
mkdir fakevault
printf "your_user_db_name" > fakevault/user.name
printf "your_user_db_password" > fakevault/user.password
printf "keystore_base64_string_printed_out_by_convertWallet.sh" > fakevault/keystore.base64
printf "truststore_base64_string_printed_out_by_convertWallet.sh" > fakevault/truststore.base64
printf "store_destination_password_used_when_executing_convertWallet.sh" > fakevault/keystore.password
```

* Edit config-local.yml and adjust the "url" property to match your db url (prefixed with "jdbc:
  oracle:thin:@")
* Also make sure the "user" property set correctly. It is recommended to use a test user for running
  this sample service.

How to start the SampleService application locally
--
Note that every time the service is started, liquibase is invoked to create/update the schema of the
configured db, the schema consist of one table named USERS (see:
`src/main/resources/db/sql/initial_schema.sql`). Liquibase will also create the 2 tables it needs to
manage its config.

1. Run `mvn clean install` to build the service, if the wallet and fakevault directories are setup
   correctly, unit tests will execute a simple "select 1 from dual" on the configured database using
   each wallet format from memory. see: `src/test/java/nomisvai/SampleServiceApplicationTest.java`
1. After a successful build, the service can be started
   with `java -jar target/sample-oracle-in-memory-wallet-1.0-SNAPSHOT.jar server target/config/config-local.yml`
1. To validate the service and see the pre-seeded test users, open a browser
   to http://localhost:8080/v1/users

Running in the OCI environment
==

Creating secrets in vault
--
see https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm for more info.
This can be done from the OCI Console or with the OCI
CLI (https://docs.oracle.com/en-us/iaas/Content/API/Concepts/cliconcepts.htm)

1. Create the following secrets in a Vault and keep the secret ids handy:

* Database user password
* Keystore printed out by the convertWallet.sh script (in Base64 format)
* TrustStore printed out by the convertWallet.sh script (in Base64 format)
* Keystore password

2. Create a Dynamic Group with the Compute Instance in
   it (https://docs.oracle.com/en-us/iaas/Content/Identity/Tasks/managingdynamicgroups.htm)

3. Create a policy allowing the dynamic group to read secrets in the vault created, for example:

```
Allow myInstanceDynamicGroup to read secret-bundle in compartment MyVaultCompartment
```

How to start the SampleService application on an OCI ComputeInstance
---

1. Edit config-cloud.yml and set the good secret id for the properties prefixed with {SECRET}

2. Create a tarball of the service and copy it to the OCI instance and un-tar it, ie:

```
tar -czvf myservice.tar.gz target/sample*SNAPSHOT.jar target/config target/classpath
scp myservice.tar.gz opc@your_compute_host_ip:.
# Then ssh on the host, untar it
ssh opc@your_compute_host_ip 
tar -zxvf  myservice.tar.gz
```

3. Start the service from the host:

```
java -jar target/sample-oracle-in-memory-wallet-1.0-SNAPSHOT.jar server target/config/config-cloud.yml
```

Troubleshooting
--

If the service cannot access the vault an error like this will be shown, review step 3 of the `
Creating secrets in vault` section to fix the error :

```
com.oracle.bmc.model.BmcException: (404, NotAuthorizedOrNotFound, false) Authorization failed or requested resource not found. (opc-request-id: 1234/1234/1234)
at com.oracle.bmc.http.internal.ResponseHelper.throwIfNotSuccessful(ResponseHelper.java:138)
at com.oracle.bmc.http.internal.ResponseConversionFunctionFactory$ValidatingParseResponseFunction.apply(ResponseConversionFunctionFactory.java:88)
at com.oracle.bmc.http.internal.ResponseConversionFunctionFactory$ValidatingParseResponseFunction.apply(ResponseConversionFunctionFactory.java:84)
at com.oracle.bmc.secrets.internal.http.GetSecretBundleConverter$1.apply(GetSecretBundleConverter.java:100)
at com.oracle.bmc.secrets.internal.http.GetSecretBundleConverter$1.apply(GetSecretBundleConverter.java:85)
at com.oracle.bmc.secrets.SecretsClient.lambda$null$0(SecretsClient.java:393)
at com.oracle.bmc.retrier.BmcGenericRetrier.doFunctionCall(BmcGenericRetrier.java:89)
at com.oracle.bmc.retrier.BmcGenericRetrier.lambda$execute$0(BmcGenericRetrier.java:60)
at com.oracle.bmc.waiter.GenericWaiter.execute(GenericWaiter.java:55)
at com.oracle.bmc.retrier.BmcGenericRetrier.execute(BmcGenericRetrier.java:51)
at com.oracle.bmc.secrets.SecretsClient.lambda$getSecretBundle$1(SecretsClient.java:389)
at com.oracle.bmc.retrier.BmcGenericRetrier.doFunctionCall(BmcGenericRetrier.java:89)
at com.oracle.bmc.retrier.BmcGenericRetrier.lambda$execute$0(BmcGenericRetrier.java:60)
at com.oracle.bmc.waiter.GenericWaiter.execute(GenericWaiter.java:55)
at com.oracle.bmc.retrier.BmcGenericRetrier.execute(BmcGenericRetrier.java:51)
at com.oracle.bmc.secrets.SecretsClient.getSecretBundle(SecretsClient.java:383)
at nomisvai.secret.OciVaultSecretRetriever.retrieveSecret(OciVaultSecretRetriever.java:30)
at nomisvai.configuration.InMemoryWalletDataSourceFactory.resolveSecret(InMemoryWalletDataSourceFactory.java:71)
at nomisvai.configuration.InMemoryWalletDataSourceFactory.build(InMemoryWalletDataSourceFactory.java:84)
at nomisvai.SampleServiceApplication.bootstrapDb(SampleServiceApplication.java:76)
at nomisvai.SampleServiceApplication.run(SampleServiceApplication.java:53)
at nomisvai.SampleServiceApplication.run(SampleServiceApplication.java:31)
at io.dropwizard.cli.EnvironmentCommand.run(EnvironmentCommand.java:59)
at io.dropwizard.cli.ConfiguredCommand.run(ConfiguredCommand.java:98)
at io.dropwizard.cli.Cli.run(Cli.java:78)
at io.dropwizard.Application.run(Application.java:94)
at nomisvai.SampleServiceApplication.main(SampleServiceApplication.java:35)
```
