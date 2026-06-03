package com.simonnepomuk.keycloak_id_austria_provider;

import org.keycloak.OAuth2Constants;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.JsonWebToken;

public class IdAustriaIdentityProvider extends OIDCIdentityProvider {
    public static final String BPK_CLAIM = "urn:pvpgvat:oidc.bpk";

    public IdAustriaIdentityProvider(KeycloakSession session, IdAustriaIdentityProviderConfig config) {
        super(session, config);
        config.setUseJwksUrl(true);
        config.setValidateSignature(true);
        config.setAccessTokenJwt(false);
        config.setDisableUserInfoService(true);
        config.setDefaultScope(SCOPE_OPENID + " " + OAuth2Constants.SCOPE_PROFILE);
        config.setCaseSensitiveOriginalUsername(true);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return false;
    }

    @Override
    public BrokeredIdentityContext getFederatedIdentity(String response) {
        BrokeredIdentityContext identity = super.getFederatedIdentity(response);
        JsonWebToken idToken = (JsonWebToken) identity.getContextData().get(VALIDATED_ID_TOKEN);
        String bpkClaim = idToken.getOtherClaims().get(BPK_CLAIM).toString();
        identity.setId(bpkClaim);
        identity.setBrokerUserId(getConfig().getAlias() + "." + bpkClaim);
        identity.setFirstName(idToken.getOtherClaims().get("given_name").toString());
        identity.setLastName(idToken.getOtherClaims().get("family_name").toString());
        return identity;
    }
}