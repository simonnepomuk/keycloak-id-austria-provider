package at.gv.bev.id_austria_provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.*;
import org.keycloak.representations.info.ServerInfoRepresentation;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static at.gv.bev.id_austria_provider.IdAustriaIdentityProviderFactory.PROVIDER_ID;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

public class IdAustriaProviderTest {

    private static final String REALM_NAME = "test-realm";
    private static final String CLIENT_ID = "test-app";

    @RegisterExtension
    static IdAustriaMockApi idAustriaMockApi = new IdAustriaMockApi(
            WireMockExtension.extensionOptions()
                    .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
                    .configureStaticDsl(true)
    );

    @RegisterExtension
    static KeycloakExtension keycloakExtension = new KeycloakExtension();

    @BeforeAll
    static void setupKeycloak() {
        keycloakExtension.createRealm(REALM_NAME);
        keycloakExtension.createClient(REALM_NAME, CLIENT_ID);
        keycloakExtension.createIdentityProvider(REALM_NAME, PROVIDER_ID, idAustriaMockApi.getIdAustriaMockConfig(CLIENT_ID));
    }

    @Test
    void testProviderIsRegistered(KeycloakContainer keycloakContainer) {
        try (Keycloak adminClient = keycloakContainer.getKeycloakAdminClient()) {
            ServerInfoRepresentation info = adminClient.serverInfo().getInfo();
            List<Map<String, String>> providers = info.getIdentityProviders();

            assertTrue(providers.stream()
                            .anyMatch(provider -> PROVIDER_ID.equals(provider.get("id"))),
                    "ID Austria provider should be registered in Keycloak");
        }
    }

    @Test
    void givenUserDoesntExist_whenLoggingInForTheFirstTime_userIsCreatedWithFirstAndLastName() throws URISyntaxException {
        var expectedUser = new IdAustriaMockApi.IdAustriaUser(
                "ZP-MH:KQMY8Sl9WsmBxrYrYOiFS2VkLyo=",
                "XXXŐzgür",
                "XXXTüzekçi"
        );

        loginUserInKeycloakThroughIdAustria(expectedUser);

        UsersResource usersResource = keycloakExtension.getAdminClient().realm(REALM_NAME).users();

        List<UserRepresentation> users = usersResource.searchByFirstName(expectedUser.firstName(), true);

        assertEquals(1, users.size(), "User should be created with first and last name from ID Austria");
        UserRepresentation actualUser = users.getFirst();
        assertEquals(expectedUser.firstName(), actualUser.getFirstName(), "First name should match the one from ID Austria");
        assertEquals(expectedUser.lastName(), actualUser.getLastName(), "Last name should match the one from ID Austria");

        List<FederatedIdentityRepresentation> federatedIdentities = usersResource.get(actualUser.getId()).getFederatedIdentity();
        assertEquals(1, federatedIdentities.size());
        assertEquals(PROVIDER_ID, federatedIdentities.getFirst().getIdentityProvider());
        assertEquals(expectedUser.bpk(), federatedIdentities.getFirst().getUserId(), "Federated Identity User ID should be the BPK value");
    }

    @Test
    void givenUserAlreadyExists_whenLoggingIn_userIsNotCreatedAgain() throws URISyntaxException {
        var user = new IdAustriaMockApi.IdAustriaUser(
                "ZP-MH:randomId=",
                "Existing",
                "User"
        );
        loginUserInKeycloakThroughIdAustria(user);
        UsersResource usersResource = keycloakExtension.getAdminClient().realm(REALM_NAME).users();
        List<UserRepresentation> usersBefore = usersResource.searchByFirstName(user.firstName(), true);
        assertEquals(1, usersBefore.size(), "User should exist before re-login");
        loginUserInKeycloakThroughIdAustria(user);
        List<UserRepresentation> usersAfter = usersResource.searchByFirstName(user.firstName(), true);
        assertEquals(1, usersAfter.size(), "User should not be created again on re-login");
    }

    private void loginUserInKeycloakThroughIdAustria(IdAustriaMockApi.IdAustriaUser user) throws URISyntaxException {
        String redirectUri = "http://localhost/callback";
        String authUrl = keycloakExtension.getContainer().getAuthServerUrl() + "/realms/" + REALM_NAME + "/protocol/openid-connect/auth" +
                "?client_id=" + CLIENT_ID +
                "&response_type=code" +
                "&redirect_uri=" + redirectUri +
                "&kc_idp_hint=" + PROVIDER_ID;

        Response initialResponse = RestAssured.given()
                .redirects()
                .follow(false)
                .get(authUrl);

        String keycloakBrokerRedirectLink = initialResponse.getHeader("Location");
        assertNotNull(keycloakBrokerRedirectLink, "Should redirect to Broker Login");
        Map<String, String> cookies = new HashMap<>(initialResponse.getCookies());

        Response brokerResponse = RestAssured.given()
                .cookies(cookies)
                .redirects().follow(false)
                .get(keycloakBrokerRedirectLink);

        String idAustriaRedirectLink = brokerResponse.getHeader("Location");
        assertNotNull(idAustriaRedirectLink, "Should redirect to IDP");
        assertTrue(idAustriaRedirectLink.contains("host.testcontainers.internal:" + idAustriaMockApi.getPort() + "/authorize"), "Should redirect to IdAustriaMockApi");

        Map<String, String> params = URLEncodedUtils.parse(new URI(idAustriaRedirectLink), StandardCharsets.UTF_8)
                .stream()
                .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
        String idpState = params.get("state");
        String idpRedirectUri = params.get("redirect_uri");
        String nonce = params.get("nonce");

        idAustriaMockApi.setupTokenEndpoint(CLIENT_ID, nonce, user);

        String callbackUrl = idpRedirectUri + "?state=" + idpState + "&code=custom-auth-code";

        if (brokerResponse.getCookies() != null) {
            cookies.putAll(brokerResponse.getCookies());
        }

        RestAssured.given()
                .cookies(cookies)
                .get(callbackUrl);
    }
}
