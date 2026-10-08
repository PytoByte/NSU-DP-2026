package server;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KeyGenerator implements Runnable {
    private static final int RSA_KEY_BITS = 8192;
    private static final String SIGN_ALGO = "SHA256withRSA";
    private static final long CERT_DAYS = 365;

    private final BlockingQueue<KeyGeneratorRequest> requestQueue;
    private final ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache;
    private final PrivateKey signingKey;
    private final X500Name issuerName;
    private final SecureRandom random = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public KeyGenerator(
            BlockingQueue<KeyGeneratorRequest> requestQueue,
            ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache,
            PrivateKey signingKey,
            X500Name issuerName
    ) {
        this.requestQueue = requestQueue;
        this.cache = cache;
        this.signingKey = signingKey;
        this.issuerName = issuerName;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                handleRequest(requestQueue.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleRequest(KeyGeneratorRequest req) {
        CompletableFuture<KeyMaterial> future = getOrStartGeneration(req.keysRequest());
        future.whenComplete((km, err) -> {
            if (err != null) {
                req.callback().onFailure(err);
            } else {
                req.callback().onSuccess(km);
            }
        });
    }

    /**
     * Если имя встречается впервые — запускает генерацию прямо в этом потоке
     * и возвращает future, который завершится по её окончании.
     * Если генерация уже идёт (или завершена) — возвращает существующий future.
     */
    private CompletableFuture<KeyMaterial> getOrStartGeneration(String name) {
        CompletableFuture<KeyMaterial> fresh = new CompletableFuture<>();
        CompletableFuture<KeyMaterial> existing = cache.putIfAbsent(name, fresh);

        if (existing != null) {
            return existing;
        }

        generateInto(name, fresh);
        return fresh;
    }

    /** Синхронная генерация. Гарантирует, что future будет завершён (успешно или с ошибкой). */
    private void generateInto(String name, CompletableFuture<KeyMaterial> future) {
        try {
            future.complete(generateKeyMaterial(name));
        } catch (Throwable e) {
            cache.remove(name, future);
            future.completeExceptionally(e);
        }
    }

    private KeyMaterial generateKeyMaterial(String name) throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = buildCertificate(name, keyPair);
        return new KeyMaterial(keyPair, cert);
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(RSA_KEY_BITS, random);
        return kpg.generateKeyPair();
    }

    private X509Certificate buildCertificate(String subjectCn, KeyPair subjectKp) throws Exception {
        Instant now = Instant.now();
        Date validBegin = Date.from(now.minus(1, ChronoUnit.MINUTES));
        Date validEnd = Date.from(now.plus(CERT_DAYS, ChronoUnit.DAYS));

        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                issuerName,
                new BigInteger(64, random).abs(),
                validBegin,
                validEnd,
                new X500NameBuilder(BCStyle.INSTANCE)
                        .addRDN(BCStyle.CN, subjectCn)
                        .build(),
                SubjectPublicKeyInfo.getInstance(subjectKp.getPublic().getEncoded())
        );

        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALGO)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signingKey);

        X509CertificateHolder holder = builder.build(signer);

        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }
}