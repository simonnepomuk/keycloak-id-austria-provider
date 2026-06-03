package at.gv.bev.id_austria_provider;

import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.models.IdentityProviderModel;

public class IdAustriaIdentityProviderConfig extends OIDCIdentityProviderConfig {
    public IdAustriaIdentityProviderConfig() {
        super();
    }

    public IdAustriaIdentityProviderConfig(IdentityProviderModel model) {
        super(model);
    }
}
