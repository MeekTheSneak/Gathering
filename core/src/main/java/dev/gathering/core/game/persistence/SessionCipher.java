package dev.gathering.core.game.persistence;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals the part of a session that nobody is entitled to read.
 *
 * <p>A library's order and the shuffle seed are, together, every card anybody will draw for
 * the rest of the game. They are written to disk because a session has to survive a restart,
 * and they are written sealed because a save file is a file: it can be copied, opened with an
 * NBT editor, and read by somebody who was never sitting at the table.
 *
 * <p>AES-GCM rather than plain AES, because this needs to detect tampering as much as it
 * needs to prevent reading. An edited library that still decrypts is a stacked deck, and it
 * would look exactly like a legitimate one. GCM makes an altered stream fail to open.
 *
 * <p>What this does not defend against, stated plainly: the server host runs the process and
 * holds the key, and could read the state out of memory with effort. That is true of every
 * online card game. What this removes is the entire attack surface below that bar.
 */
public final class SessionCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 96 bits, which is what GCM is specified around; longer gets hashed and gains nothing. */
    private static final int NONCE_BYTES = 12;

    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionCipher() {
    }

    public static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(KEY_BITS);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("This JVM cannot generate an AES key", e);
        }
    }

    public static SecretKey keyFrom(byte[] raw) {
        if (raw == null || raw.length != KEY_BITS / 8) {
            throw new IllegalArgumentException("A session key is " + KEY_BITS / 8 + " bytes");
        }
        return new SecretKeySpec(raw, ALGORITHM);
    }

    public static byte[] rawOf(SecretKey key) {
        return key.getEncoded();
    }

    /**
     * Seals a stream. The nonce goes in front of the ciphertext.
     *
     * <p>A fresh random nonce every time, which is the one thing GCM cannot forgive being
     * wrong: reusing a nonce under the same key does not weaken the encryption, it breaks it.
     */
    public static byte[] seal(SecretKey key, byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] sealed = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, sealed, 0, nonce.length);
            System.arraycopy(ciphertext, 0, sealed, nonce.length, ciphertext.length);
            return sealed;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a session", e);
        }
    }

    /**
     * Opens a sealed stream.
     *
     * @throws SealedStreamException if the key is wrong or the stream has been altered - the
     *                               two are deliberately indistinguishable from out here
     */
    public static byte[] open(SecretKey key, byte[] sealed) throws SealedStreamException {
        if (sealed == null || sealed.length <= NONCE_BYTES) {
            throw new SealedStreamException("The sealed part of this session is missing or truncated");
        }
        byte[] nonce = Arrays.copyOfRange(sealed, 0, NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(sealed, NONCE_BYTES, sealed.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            // Deliberately without the cause: which of "wrong key" and "edited file" it was
            // is not something a caller needs and not something to help an attacker learn.
            throw new SealedStreamException("The sealed part of this session will not open");
        }
    }

    /** A sealed stream that cannot be opened, because the key is wrong or it was altered. */
    public static class SealedStreamException extends Exception {

        private static final long serialVersionUID = 1L;

        public SealedStreamException(String message) {
            super(message);
        }
    }
}
