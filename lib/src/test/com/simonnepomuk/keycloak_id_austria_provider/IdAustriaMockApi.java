package com.simonnepomuk.keycloak_id_austria_provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.keycloak.common.util.Time;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessTokenResponse;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.keycloak.util.JsonSerialization.writeValueAsString;

public class IdAustriaMockApi extends WireMockExtension {

    private PrivateKey privateKey;
    private JWK jwk;

    public IdAustriaMockApi(WireMockExtension.Builder builder) {
        super(builder);
    }

    @Override
    protected void onBeforeAll(ExtensionContext extensionContext, WireMockRuntimeInfo wireMockRuntimeInfo) {
        extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).put("exposedHostPorts", Set.of(this.getPort(), this.getHttpsPort()));
        createJwks();
    }

    @Override
    protected void onBeforeEach(ExtensionContext extensionContext, WireMockRuntimeInfo wireMockRuntimeInfo) {
        try {
            JSONWebKeySet jwks = new JSONWebKeySet();
            jwks.setKeys(new JWK[]{jwk});
            stubFor(get(urlEqualTo("/jwks"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(writeValueAsString(jwks))
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType().equals(IdAustriaMockApi.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return this;
    }

    public Map<String, String> getIdAustriaMockConfig(String clientId) {
        Map<String, String> config = new HashMap<>();
        String wireMockBaseUrl = "http://host.testcontainers.internal:" + this.getPort();
        config.put("authorizationUrl", wireMockBaseUrl + "/authorize");
        config.put("tokenUrl", wireMockBaseUrl + "/token");
        config.put("jwksUrl", wireMockBaseUrl + "/jwks");
        config.put("issuer", wireMockBaseUrl);
        config.put("clientAuthMethod", "client_secret_post");
        config.put("clientId", clientId);
        config.put("clientSecret", "secret");
        return config;
    }

    public void setupTokenEndpoint(String clientId, String nonce, IdAustriaUser user) {
        try {
            AccessTokenResponse tokenResponse = new AccessTokenResponse();
            tokenResponse.setToken("access-token");
            tokenResponse.setExpiresIn(300);
            tokenResponse.setIdToken(createIdToken(clientId, nonce, user));

            stubFor(post(urlEqualTo("/token"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody(writeValueAsString(tokenResponse)))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createIdToken(String clientId, String nonce, IdAustriaUser user) {
        try {
            return new JWSBuilder()
                    .kid(jwk.getKeyId())
                    .type("JWT")
                    .jsonContent(createClaims(clientId, nonce, user))
                    .rsa256(privateKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> createClaims(String clientId, String nonce, IdAustriaUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", UUID.randomUUID().toString());
        claims.put("iss", "http://host.testcontainers.internal:" + this.getPort());
        claims.put("aud", clientId);
        claims.put("exp", Time.currentTime() + 3000);
        claims.put("iat", Time.currentTime());
        claims.put("given_name", user.firstName);
        claims.put("family_name", user.lastName);
        claims.put("urn:pvpgvat:oidc.bpk", user.bpk);
        if (nonce != null) {
            claims.put("nonce", nonce);
        }
        return claims;
    }

    public void createJwks() {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String keyId = UUID.randomUUID().toString();
            JWK jwk = JWKBuilder.create().kid(keyId).algorithm("RS256").rsa(keyPair.getPublic());
            this.privateKey = keyPair.getPrivate();
            this.jwk = jwk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record IdAustriaUser(String bpk, String firstName, String lastName) {
    }
}
