package at.gv.bev.id_austria_provider;

import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

public class IdAustriaIdentityProviderFactory extends OIDCIdentityProviderFactory {
    public static final String PROVIDER_ID = "id-austria-oidc";

    @Override
    public String getName() {
        return "ID Austria OIDC";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public IdAustriaIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new IdAustriaIdentityProvider(session, new IdAustriaIdentityProviderConfig(model));
    }

    @Override
    public IdAustriaIdentityProviderConfig createConfig() {
        return new IdAustriaIdentityProviderConfig();
    }

    @Override
    public String getHelpText() {
        return "ID Austria Identity Provider using OpenID Connect protocol.";
    }

}