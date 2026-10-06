package org.example.server;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

public record KeyMaterial(
        KeyPair keyPair,
        X509Certificate certificate
) {}