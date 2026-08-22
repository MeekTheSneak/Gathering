package dev.gathering.core.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.SecretKey;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionCipherTest {

    @Property(tries = 500)
    void whatIsSealedComesBack(@ForAll @Size(max = 4096) byte[] plaintext) throws Exception {
        SecretKey key = SessionCipher.newKey();

        byte[] sealed = SessionCipher.seal(key, plaintext);

        assertThat(SessionCipher.open(key, sealed)).isEqualTo(plaintext);
    }

    @Property(tries = 500)
    void theSealedFormIsNotThePlainOne(@ForAll @Size(min = 16, max = 4096) byte[] plaintext) {
        SecretKey key = SessionCipher.newKey();

        assertThat(SessionCipher.seal(key, plaintext)).isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("sealing the same thing twice does not produce the same bytes")
    void everySealUsesAFreshNonce() {
        // Reusing a nonce under one key does not weaken GCM, it breaks it - two messages
        // sealed with the same nonce leak each other.
        SecretKey key = SessionCipher.newKey();
        byte[] plaintext = "the top of the library".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(SessionCipher.seal(key, plaintext)).isNotEqualTo(SessionCipher.seal(key, plaintext));
    }

    @Test
    @DisplayName("another key does not open it")
    void theWrongKeyIsRefused() {
        byte[] sealed = SessionCipher.seal(SessionCipher.newKey(), new byte[] {1, 2, 3});

        assertThatThrownBy(() -> SessionCipher.open(SessionCipher.newKey(), sealed))
                .isInstanceOf(SessionCipher.SealedStreamException.class);
    }

    @Test
    @DisplayName("an edited stream will not open, so a stacked deck cannot be smuggled in")
    void tamperingIsDetected() {
        // The reason this is GCM and not plain AES. A library edited on disk that still
        // decrypted would look exactly like a legitimate one.
        SecretKey key = SessionCipher.newKey();
        byte[] sealed = SessionCipher.seal(key, "library order".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));

        for (int index = 0; index < sealed.length; index++) {
            byte[] edited = sealed.clone();
            edited[index] ^= 0x01;
            assertThatThrownBy(() -> SessionCipher.open(key, edited))
                    .describedAs("flipping a bit at %s went undetected", index)
                    .isInstanceOf(SessionCipher.SealedStreamException.class);
        }
    }

    @Test
    @DisplayName("a key survives being written down and read back")
    void keysRoundTrip() throws Exception {
        SecretKey key = SessionCipher.newKey();
        byte[] sealed = SessionCipher.seal(key, new byte[] {9, 9, 9});

        SecretKey restored = SessionCipher.keyFrom(SessionCipher.rawOf(key));

        assertThat(SessionCipher.open(restored, sealed)).containsExactly(9, 9, 9);
    }

    @Test
    void aTruncatedStreamIsRefusedRatherThanCrashing() {
        assertThatThrownBy(() -> SessionCipher.open(SessionCipher.newKey(), new byte[] {1, 2}))
                .isInstanceOf(SessionCipher.SealedStreamException.class);
    }
}
