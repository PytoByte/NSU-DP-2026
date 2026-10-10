package server;

import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public record KeyResponse(
        PrivateKey privateKey,
        X509Certificate certificate
) {
    public ByteBuffer encode() throws Exception {
        byte[] keyBytes  = privateKey.getEncoded();
        byte[] certBytes = certificate.getEncoded();

        ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + keyBytes.length
                + Integer.BYTES + certBytes.length);
        buf.putInt(keyBytes.length).put(keyBytes);
        buf.putInt(certBytes.length).put(certBytes);
        buf.flip();
        return buf;
    }
}