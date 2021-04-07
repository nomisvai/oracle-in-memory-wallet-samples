#!/bin/bash

if [ "$#" -ne 3 ]; then
    echo "Usage: convertWallet <wallet_dir> <source_password> <destination_password>"
    exit 1;
fi

WALLET_DIR=$1
SOURCE_KEY_PASS=$2
DESTINATION_KEY_PASS=$3

mkdir -p $HOME/jar

if [ ! -f $HOME/jar/bcpkix-fips-1.0.2.jar ] ; then
  curl --output $HOME/jar/bcpkix-fips-1.0.2.jar https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-fips/1.0.2/bcpkix-fips-1.0.2.jar
fi

if [ ! -f $HOME/jar/bc-fips-1.0.2.jar ] ; then
  curl --output $HOME/jar/bc-fips-1.0.2.jar https://repo1.maven.org/maven2/org/bouncycastle/bc-fips/1.0.2/bc-fips-1.0.2.jar
fi

rm -f $WALLET_DIR/keystore.bcfks $WALLET_DIR/truststore.bcfks

keytool -importkeystore \
-srckeystore $WALLET_DIR/keystore.jks \
-destkeystore $WALLET_DIR/keystore.bcfks \
-srcstoretype JKS \
-deststoretype BCFKS \
-srcstorepass $SOURCE_KEY_PASS \
-deststorepass $DESTINATION_KEY_PASS \
-provider org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider \
-providerpath $HOME/jar/bcpkix-fips-1.0.2.jar:$HOME/jar/bc-fips-1.0.2.jar

keytool -importkeystore \
-srckeystore $WALLET_DIR/truststore.jks \
-destkeystore $WALLET_DIR/truststore.bcfks \
-srcstoretype JKS \
-deststoretype BCFKS \
-srcstorepass $SOURCE_KEY_PASS \
-deststorepass $DESTINATION_KEY_PASS \
-provider org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider \
-providerpath $HOME/jar/bcpkix-fips-1.0.2.jar:$HOME/jar/bc-fips-1.0.2.jar

echo KEYSTORE:
base64 $WALLET_DIR/keystore.bcfks
echo TRUSTSTORE:
base64 $WALLET_DIR/truststore.bcfks
