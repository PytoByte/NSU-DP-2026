package org.example.server;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record KeyMaterial(
        KeyPair keyPair,
        X509Certificate certificate
) {

    /**
     * Сериализует материал в буфер для отправки по сети:
     * {@code [4 байта len][private key DER][4 байта len][certificate DER]}.
     * Позиция буфера — 0, limit — конец данных.
     */
    public ByteBuffer encode() throws Exception {
        byte[] keyBytes  = keyPair.getPrivate().getEncoded();
        byte[] certBytes = certificate.getEncoded();

        ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + keyBytes.length
                + Integer.BYTES + certBytes.length);
        buf.putInt(keyBytes.length).put(keyBytes);
        buf.putInt(certBytes.length).put(certBytes);
        buf.flip();
        return buf;
    }
}