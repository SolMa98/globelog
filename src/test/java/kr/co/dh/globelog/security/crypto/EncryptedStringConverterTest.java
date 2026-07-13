package kr.co.dh.globelog.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter("test-secret-key");

    @Test
    void decryptingEncryptedValueReturnsOriginal() {
        String plain = "JBSWY3DPEHPK3PXP";

        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void nullRemainsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void encryptingSameValueTwiceProducesDifferentCiphertext() {
        String plain = "same-value";

        String encryptedFirst = converter.convertToDatabaseColumn(plain);
        String encryptedSecond = converter.convertToDatabaseColumn(plain);

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
    }

    @Test
    void blankSecretKeyThrowsOnConstruction() {
        assertThatThrownBy(() -> new EncryptedStringConverter(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EncryptedStringConverter(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void converterWithDifferentKeyCannotDecryptOthersCiphertext() {
        EncryptedStringConverter other = new EncryptedStringConverter("different-secret-key");
        String encrypted = converter.convertToDatabaseColumn("secret-value");

        assertThatThrownBy(() -> other.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }
}