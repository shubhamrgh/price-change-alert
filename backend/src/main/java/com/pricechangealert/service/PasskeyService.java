package com.pricechangealert.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.pricechangealert.model.PasskeyChallenge;
import com.pricechangealert.model.PasskeyCredential;
import com.pricechangealert.model.UserAccount;
import com.pricechangealert.repository.PasskeyChallengeRepository;
import com.pricechangealert.repository.PasskeyCredentialRepository;
import com.pricechangealert.repository.UserAccountRepository;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasskeyService {

    public record RegistrationFinish(String challengeId, String rawId, String clientDataJSON,
                                     String attestationObject, List<String> transports, String name) { }
    public record LoginFinish(String challengeId, String rawId, String clientDataJSON,
                              String authenticatorData, String signature) { }
    public record PasskeyView(Long id, String name, Instant createdAt, Instant lastUsedAt) { }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

    private final PasskeyCredentialRepository credentials;
    private final PasskeyChallengeRepository challenges;
    private final UserAccountRepository users;
    private final SecureRandom random = new SecureRandom();
    private final Duration challengeTtl;
    private final String rpId;
    private final String rpName;
    private final Set<String> allowedOrigins;

    public PasskeyService(PasskeyCredentialRepository credentials,
                          PasskeyChallengeRepository challenges,
                          UserAccountRepository users,
                          @Value("${price-change-alert.auth.passkeys.challenge-ttl:5m}") Duration challengeTtl,
                          @Value("${price-change-alert.auth.passkeys.rp-id:localhost}") String rpId,
                          @Value("${price-change-alert.auth.passkeys.rp-name:Tailify}") String rpName,
                          @Value("${price-change-alert.auth.passkeys.origins:http://localhost:8080,http://localhost:5173}")
                          String origins) {
        this.credentials = credentials;
        this.challenges = challenges;
        this.users = users;
        this.challengeTtl = challengeTtl;
        this.rpId = rpId.trim().toLowerCase(Locale.ROOT);
        this.rpName = rpName.trim();
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<PasskeyView> list(String userId) {
        return credentials.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(value -> new PasskeyView(value.getId(), value.getName(),
                        value.getCreatedAt(), value.getLastUsedAt())).toList();
    }

    @Transactional
    public void delete(String userId, Long id) {
        credentials.findById(id).filter(value -> value.getUserId().equals(userId))
                .ifPresent(credentials::delete);
    }

    @Transactional
    public Map<String, Object> registrationOptions(UserAccount user) {
        Challenge created = createChallenge(user.getId(), PasskeyChallenge.Purpose.REGISTER);
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", created.raw());
        publicKey.put("rp", Map.of("id", rpId, "name", rpName));
        publicKey.put("user", Map.of(
                "id", B64.encodeToString(user.getId().getBytes(StandardCharsets.UTF_8)),
                "name", user.getEmail(), "displayName", user.getEmail()));
        publicKey.put("pubKeyCredParams", List.of(
                Map.of("type", "public-key", "alg", -7),
                Map.of("type", "public-key", "alg", -257)));
        publicKey.put("timeout", challengeTtl.toMillis());
        publicKey.put("attestation", "none");
        publicKey.put("authenticatorSelection", Map.of(
                "residentKey", "preferred", "userVerification", "preferred"));
        publicKey.put("excludeCredentials", credentials.findAllByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                .map(value -> Map.<String, Object>of("type", "public-key", "id", value.getCredentialId()))
                .toList());
        return Map.of("challengeId", created.id(), "publicKey", publicKey);
    }

    @Transactional
    public PasskeyView finishRegistration(UserAccount user, RegistrationFinish finish) {
        PasskeyChallenge challenge = consumeChallenge(finish.challengeId(), user.getId(),
                PasskeyChallenge.Purpose.REGISTER);
        ClientData client = verifyClientData(finish.clientDataJSON(), "webauthn.create", challenge);
        byte[] attestationBytes = decode(finish.attestationObject(), "attestation object");
        JsonNode attestation;
        try {
            attestation = CBOR.readTree(attestationBytes);
        } catch (Exception exception) {
            throw invalidPasskey();
        }
        byte[] authData = binary(attestation.get("authData"));
        ParsedRegistration parsed = parseRegistration(authData);
        byte[] rawId = decode(finish.rawId(), "credential ID");
        if (!MessageDigest.isEqual(rawId, parsed.credentialId())) throw invalidPasskey();
        if (credentials.findByCredentialId(B64.encodeToString(rawId)).isPresent()) {
            throw new IllegalArgumentException("This passkey is already registered");
        }
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(user.getId());
        credential.setCredentialId(B64.encodeToString(rawId));
        credential.setPublicKeyCose(B64.encodeToString(parsed.publicKeyCose()));
        credential.setAlgorithm(parsed.algorithm());
        credential.setSignCount(parsed.signCount());
        String requestedName = finish.name() == null ? "" : finish.name().trim();
        credential.setName(requestedName.isBlank() ? "Passkey" : requestedName.substring(0,
                Math.min(requestedName.length(), 80)));
        credentials.save(credential);
        return new PasskeyView(credential.getId(), credential.getName(), credential.getCreatedAt(), null);
    }

    @Transactional
    public Map<String, Object> loginOptions(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No passkey is available for that account"));
        List<PasskeyCredential> available = credentials.findAllByUserIdOrderByCreatedAtAsc(user.getId());
        if (available.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No passkey is available for that account");
        Challenge created = createChallenge(user.getId(), PasskeyChallenge.Purpose.LOGIN);
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", created.raw());
        publicKey.put("rpId", rpId);
        publicKey.put("timeout", challengeTtl.toMillis());
        publicKey.put("userVerification", "preferred");
        publicKey.put("allowCredentials", available.stream().map(value -> Map.<String, Object>of(
                "type", "public-key", "id", value.getCredentialId())).toList());
        return Map.of("challengeId", created.id(), "publicKey", publicKey);
    }

    @Transactional
    public UserAccount finishLogin(LoginFinish finish) {
        PasskeyCredential credential = credentials.findByCredentialId(normalizeB64(finish.rawId()))
                .orElseThrow(this::invalidPasskey);
        PasskeyChallenge challenge = consumeChallenge(finish.challengeId(), credential.getUserId(),
                PasskeyChallenge.Purpose.LOGIN);
        ClientData client = verifyClientData(finish.clientDataJSON(), "webauthn.get", challenge);
        byte[] authenticatorData = decode(finish.authenticatorData(), "authenticator data");
        ParsedAuthData parsed = parseAuthenticatorData(authenticatorData, false);
        byte[] clientHash = sha256(client.raw());
        byte[] signed = ByteBuffer.allocate(authenticatorData.length + clientHash.length)
                .put(authenticatorData).put(clientHash).array();
        try {
            PublicKey key = publicKey(credential);
            Signature verifier = Signature.getInstance(credential.getAlgorithm() == -7
                    ? "SHA256withECDSA" : "SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(signed);
            if (!verifier.verify(decode(finish.signature(), "signature"))) throw invalidPasskey();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPasskey();
        }
        if (credential.getSignCount() > 0 && parsed.signCount() > 0
                && parsed.signCount() <= credential.getSignCount()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This passkey may have been copied and was rejected");
        }
        if (parsed.signCount() > credential.getSignCount()) credential.setSignCount(parsed.signCount());
        credential.setLastUsedAt(Instant.now());
        credentials.save(credential);
        return users.findById(credential.getUserId()).orElseThrow(this::invalidPasskey);
    }

    @Transactional
    public long deleteExpiredChallenges() {
        return challenges.deleteByExpiresAtBefore(Instant.now());
    }

    public boolean available() {
        return !rpId.isBlank() && !allowedOrigins.isEmpty();
    }

    private Challenge createChallenge(String userId, PasskeyChallenge.Purpose purpose) {
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String raw = B64.encodeToString(randomBytes);
        PasskeyChallenge challenge = new PasskeyChallenge();
        challenge.setId(UUID.randomUUID().toString());
        challenge.setUserId(userId);
        challenge.setPurpose(purpose.name());
        challenge.setChallengeHash(AuthTokenService.hash(raw));
        challenge.setExpiresAt(Instant.now().plus(challengeTtl));
        challenges.save(challenge);
        return new Challenge(challenge.getId(), raw);
    }

    private PasskeyChallenge consumeChallenge(String id, String userId, PasskeyChallenge.Purpose purpose) {
        PasskeyChallenge challenge = challenges.findById(id == null ? "" : id)
                .orElseThrow(this::invalidPasskey);
        challenges.delete(challenge);
        if (!challenge.getUserId().equals(userId) || !challenge.getPurpose().equals(purpose.name())
                || !challenge.getExpiresAt().isAfter(Instant.now())) throw invalidPasskey();
        return challenge;
    }

    private ClientData verifyClientData(String encoded, String expectedType, PasskeyChallenge challenge) {
        byte[] raw = decode(encoded, "client data");
        try {
            JsonNode json = JSON.readTree(raw);
            String type = json.path("type").asText();
            String webChallenge = json.path("challenge").asText();
            String origin = json.path("origin").asText();
            if (!expectedType.equals(type) || !allowedOrigins.contains(origin)
                    || !MessageDigest.isEqual(
                    AuthTokenService.hash(webChallenge).getBytes(StandardCharsets.US_ASCII),
                    challenge.getChallengeHash().getBytes(StandardCharsets.US_ASCII))) {
                throw invalidPasskey();
            }
            return new ClientData(raw);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPasskey();
        }
    }

    private ParsedRegistration parseRegistration(byte[] authData) {
        ParsedAuthData base = parseAuthenticatorData(authData, true);
        int offset = 37 + 16;
        if (authData.length < offset + 2) throw invalidPasskey();
        int credentialLength = Short.toUnsignedInt(ByteBuffer.wrap(authData, offset, 2).getShort());
        offset += 2;
        if (authData.length < offset + credentialLength + 1) throw invalidPasskey();
        byte[] credentialId = Arrays.copyOfRange(authData, offset, offset + credentialLength);
        offset += credentialLength;
        try {
            JsonNode cose = CBOR.readTree(new ByteArrayInputStream(authData, offset, authData.length - offset));
            int algorithm = cose.path("3").asInt(Integer.MIN_VALUE);
            if (algorithm != -7 && algorithm != -257) throw invalidPasskey();
            return new ParsedRegistration(credentialId, CBOR.writeValueAsBytes(cose), algorithm,
                    base.signCount());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPasskey();
        }
    }

    private ParsedAuthData parseAuthenticatorData(byte[] authData, boolean requireAttestedData) {
        if (authData == null || authData.length < 37) throw invalidPasskey();
        byte[] expectedRpHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expectedRpHash, Arrays.copyOfRange(authData, 0, 32))) throw invalidPasskey();
        int flags = Byte.toUnsignedInt(authData[32]);
        if ((flags & 0x01) == 0 || (requireAttestedData && (flags & 0x40) == 0)) throw invalidPasskey();
        long signCount = Integer.toUnsignedLong(ByteBuffer.wrap(authData, 33, 4).getInt());
        return new ParsedAuthData(signCount);
    }

    private PublicKey publicKey(PasskeyCredential credential) throws Exception {
        JsonNode cose = CBOR.readTree(B64D.decode(credential.getPublicKeyCose()));
        if (credential.getAlgorithm() == -7) {
            byte[] x = binary(cose.get("-2"));
            byte[] y = binary(cose.get("-3"));
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ec = parameters.getParameterSpec(ECParameterSpec.class);
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                    new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), ec));
        }
        if (credential.getAlgorithm() == -257) {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, binary(cose.get("-1"))),
                    new BigInteger(1, binary(cose.get("-2")))));
        }
        throw invalidPasskey();
    }

    private static byte[] binary(JsonNode value) {
        try {
            return value == null ? null : value.binaryValue();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey verification failed");
        }
    }

    private static byte[] decode(String value, String label) {
        try {
            return B64D.decode(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label);
        }
    }

    private static String normalizeB64(String value) {
        return B64.encodeToString(decode(value, "credential ID"));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private ResponseStatusException invalidPasskey() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey verification failed");
    }

    private record Challenge(String id, String raw) { }
    private record ClientData(byte[] raw) { }
    private record ParsedAuthData(long signCount) { }
    private record ParsedRegistration(byte[] credentialId, byte[] publicKeyCose,
                                      int algorithm, long signCount) { }
}
