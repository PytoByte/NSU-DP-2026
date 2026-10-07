package org.example.server;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;

/**
 * Читает приватный ключ из PEM-файла.
 * Поддерживает оба варианта PKCS#8:
 *   - незашифрованный: BEGIN PRIVATE KEY (пароль не нужен)
 *   - зашифрованный: BEGIN ENCRYPTED PRIVATE KEY (пароль обязателен)
 */
public final class SigningKeyLoader {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * @param path     путь к PEM-файлу
     * @param password пароль для расшифровки; может быть {@code null} для незашифрованного PEM
     * @return приватный ключ
     * @throws IOException если файл не читается или PEM не распознан
     */
    public static PrivateKey load(Path path, char[] password) throws Exception {
        try (Reader reader = Files.newBufferedReader(path);
             PEMParser parser = new PEMParser(reader)) {

            Object object = parser.readObject();
            if (object == null) {
                throw new IOException("Empty PEM file: " + path);
            }

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME);

            if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }

            if (object instanceof PKCS8EncryptedPrivateKeyInfo encrypted) {
                return decryptAndConvert(encrypted, password, converter);
            }

            throw new IOException("Unsupported PEM object: " + object.getClass().getName()
                    + " (expected PKCS#8 private key)");
        }
    }

    private static PrivateKey decryptAndConvert(
            PKCS8EncryptedPrivateKeyInfo encrypted,
            char[] password,
            JcaPEMKeyConverter converter) throws Exception {

        if (password == null || password.length == 0) {
            throw new IOException("PEM is encrypted, but no password provided");
        }

        InputDecryptorProvider decryptor = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(password);

        return converter.getPrivateKey(encrypted.decryptPrivateKeyInfo(decryptor));
    }
}
