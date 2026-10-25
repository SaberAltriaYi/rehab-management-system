package cn.iocoder.yudao.module.rehab.service.hep;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

/** The Core login session is authoritative; HEP receives a short-lived signed token. */
@Service
@ConditionalOnProperty(name="REHAB_HEP_BRIDGE_ENABLED",havingValue="true")
public class RehabHepTokenService {
    private final RSAPrivateCrtKey privateKey;
    private final RSAKey publicJwk;
    private final String issuer;
    public RehabHepTokenService(
        @Value("${REHAB_HEP_JWT_PRIVATE_KEY_FILE:}") String keyPath,
        @Value("${REHAB_HEP_JWT_ISSUER:http://127.0.0.1:48080}") String issuer) throws Exception {
        if(keyPath.isBlank()) throw new IllegalStateException("HEP JWT signing key path required");
        String pem=Files.readString(Path.of(keyPath));
        String base64=pem.replace("-----BEGIN PRIVATE KEY-----","")
            .replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        privateKey=(RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        if(privateKey.getModulus().bitLength()<3072) throw new IllegalStateException("HEP signing key too short");
        RSAPublicKey publicKey=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(
            new RSAPublicKeySpec(privateKey.getModulus(),privateKey.getPublicExponent()));
        this.publicJwk=new RSAKey.Builder(publicKey).keyID("rehab-core-hep-v1").build();
        this.issuer=issuer;
    }
    public Map<String,Object> jwks(){return Map.of("keys",List.of(publicJwk.toPublicJWK().toJSONObject()));}
    public Map<String,Object> token(Long subject,String role) {
        if(subject==null || subject<=0 || !Set.of("ADMIN","THERAPIST","PATIENT").contains(role))
            throw new IllegalArgumentException("invalid HEP subject or role");
        Long tenant=TenantContextHolder.getRequiredTenantId();
        if(tenant==null || tenant<=0) throw new IllegalStateException("Core tenant context unavailable");
        try {
            Instant now=Instant.now();
            JWTClaimsSet claims=new JWTClaimsSet.Builder()
                .issuer(issuer).subject(String.valueOf(subject)).audience("hep-api")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString())
                .claim("tenant_id",String.valueOf(tenant))
                .claim("roles",List.of(role)).build();
            SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(publicJwk.getKeyID()).build(),claims);
            jwt.sign(new RSASSASigner(privateKey));
            return Map.of("accessToken",jwt.serialize(),"tokenType","Bearer","expiresIn",900);
        } catch (Exception e) {throw new IllegalStateException("HEP token signing unavailable");}
    }
}
