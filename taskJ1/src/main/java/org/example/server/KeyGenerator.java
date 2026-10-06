package org.example.server;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
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

public class KeyGenerator implements Runnable, AutoCloseable {

    private static final int RSA_KEY_BITS = 8192;
    private static final String SIGN_ALGO = "SHA256withRSA";
    private static final long CERT_DAYS = 365;

    private final BlockingQueue<Request> requestQueue;
    private final BlockingQueue<Response> responseQueue;
    private final ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache;
    private final PrivateKey signingKey;
    private final X500Name issuerName;
    private final SecureRandom random = new SecureRandom();

    private volatile boolean running = true;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public KeyGenerator(
            BlockingQueue<Request> requestQueue,
            BlockingQueue<Response> responseQueue,
            ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> cache,
            PrivateKey signingKey,
            String issuerDn
    ) {
        this.requestQueue = requestQueue;
        this.responseQueue = responseQueue;
        this.cache = cache;
        this.signingKey = signingKey;
        this.issuerName = new X500Name(issuerDn);
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            Request req = takeRequest();
            if (req == null) {
                return;
            }
            handleRequest(req);
        }
    }

    private Request takeRequest() {
        try {
            return requestQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void handleRequest(Request req) {
        CompletableFuture<KeyMaterial> future = getOrStartGeneration(req.keysRequest());
        future.whenComplete((km, err) -> enqueueResponse(req, km, err));
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
        } catch (Exception e) {
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

    private void enqueueResponse(Request req, KeyMaterial km, Throwable err) {
        Response response = (err == null)
                ? Response.success(req.keysRequest(), km.keyPair(), km.certificate(), req.socketChannel())
                : Response.failure(req.keysRequest(), req.socketChannel(), err);

        try {
            responseQueue.put(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
                new X500Name("CN=" + subjectCn),
                SubjectPublicKeyInfo.getInstance(subjectKp.getPublic().getEncoded())
        );

        addStandardExtensions(builder, subjectKp);

        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALGO)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signingKey);

        X509CertificateHolder holder = builder.build(signer);

        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private void addStandardExtensions(
            X509v3CertificateBuilder builder,
            KeyPair subjectKp
    ) throws Exception {
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        builder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(false)
        ).addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment)
        ).addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extUtils.createSubjectKeyIdentifier(subjectKp.getPublic())
        );
    }

    public void shutdown() {
        running = false;
    }

    @Override
    public void close() {
        shutdown();
    }
}