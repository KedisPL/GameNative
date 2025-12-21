package app.gamenative;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Crypto class that uses the Android KeyStore
 * Reference: https://github.com/philipplackner/EncryptedDataStore
 */
public final class Crypto {

    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
    private static final String KEY_ALIAS = "pluvia_secret";
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;
    private static final String TRANSFORMATION = ALGORITHM + "/" + BLOCK_MODE + "/" + PADDING;

    private static final KeyStore keyStore;

    // Static initializer block to replace Kotlin's property initialization logic
    static {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize KeyStore", e);
        }
    }

    // Private constructor to mimic 'object' behavior (no instances allowed)
    private Crypto() {
    }

    // Thread 'Safety'
    private static Cipher getCipher() {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static SecretKey getKey() {
        try {
            KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            return createKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static SecretKey createKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore");

            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .setKeySize(256)
                    .build();

            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] encrypt(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Input bytes cannot be empty");
        }

        try {
            Cipher cipher = getCipher();
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encryptedBytes = cipher.doFinal(bytes);
            byte[] iv = cipher.getIV();

            // Combine IV and Encrypted Data (Kotlin's: return cipher.iv + cipher.doFinal(bytes))
            byte[] result = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, result, iv.length, encryptedBytes.length);

            return result;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decrypt(byte[] bytes) {
        try {
            Cipher cipher = getCipher();
            int blockSize = cipher.getBlockSize();

            if (bytes.length <= blockSize) {
                throw new IllegalArgumentException("Input bytes too short to contain IV and data. " +
                        "Minimum length is " + (blockSize + 1));
            }

            // Extract IV and Data (Kotlin's: copyOfRange)
            byte[] iv = Arrays.copyOfRange(bytes, 0, blockSize);
            byte[] data = Arrays.copyOfRange(bytes, blockSize, bytes.length);

            cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));
            return cipher.doFinal(data);

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
