package com.simonnepomuk.keycloak_id_austria_provider;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.extension.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Collections.emptySet;


public class KeycloakExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final String DEFAULT_KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:latest";
    private final String image;
    private KeycloakContainer keycloak;

    public KeycloakExtension() {
        this(DEFAULT_KEYCLOAK_IMAGE);
    }

    public KeycloakExtension(String image) {
        this.image = image;
    }

    public KeycloakContainer getContainer() {
        return keycloak;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Set<Integer> exposedPorts = context.getStore(ExtensionContext.Namespace.GLOBAL).getOrDefault("exposedHostPorts", Set.class, emptySet());
        Testcontainers.exposeHostPorts(exposedPorts.stream().mapToInt(Integer::intValue).toArray());
        keycloak = new KeycloakContainer(image);
        keycloak.addEnv("KC_LOG_LEVEL_ORG_KEYCLOAK", "DEBUG");

        File jarFile = findProviderJar();
        if (jarFile == null) {
            throw new RuntimeException("Provider JAR not found in build/libs. Please run './gradlew jar' before running integration tests.");
        }

        keycloak.withCopyFileToContainer(MountableFile.forHostPath(jarFile.toPath()), "/opt/keycloak/providers/id-austria-provider.jar");

        keycloak.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (keycloak != null) {
            keycloak.stop();
        }
    }

    private static File findProviderJar() {
        File libsDir = Paths.get("build", "libs").toFile();
        if (!libsDir.exists() || !libsDir.isDirectory()) {
            return null;
        }
        return Arrays.stream(Objects.requireNonNull(libsDir.listFiles())).filter(f -> f.getName().endsWith(".jar") && !f.getName().contains("-plain") && !f.getName().contains("-javadoc") && !f.getName().contains("-sources")).findFirst().orElse(null);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(KeycloakContainer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return getContainer();
    }

    public Keycloak getAdminClient() {
        return keycloak.getKeycloakAdminClient();
    }

    public void createRealm(String realmName) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(realmName);
        realm.setEnabled(true);
        try {
            getAdminClient().realms().create(realm);
        } catch (Exception e) {
            // Ignore if exists
        }
    }

    public void createClient(String realmName, String clientId) {
        org.keycloak.representations.idm.ClientRepresentation client = new org.keycloak.representations.idm.ClientRepresentation();
        client.setClientId(clientId);
        client.setEnabled(true);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setRedirectUris(java.util.Collections.singletonList("http://localhost/callback"));

        getAdminClient().realm(realmName).clients().create(client);
    }

    public void createIdentityProvider(String realmName, String providerId, Map<String, String> config) {
        IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
        idp.setAlias(providerId);
        idp.setProviderId(providerId);
        idp.setEnabled(true);
        idp.setStoreToken(true);
        idp.setLinkOnly(false);
        idp.setFirstBrokerLoginFlowAlias("first broker login");
        idp.setConfig(config);

        RealmResource realmResource = getAdminClient().realm(realmName);
        realmResource.identityProviders().create(idp);

        disableUpdateProfileFlow(realmName);
    }

    private void disableUpdateProfileFlow(String realmName) {
        String flowAlias = "first broker login";
        List<AuthenticationExecutionInfoRepresentation> executions = getAdminClient().realm(realmName).flows().getExecutions(flowAlias);
        for (AuthenticationExecutionInfoRepresentation execution : executions) {
            if ("idp-review-profile".equals(execution.getProviderId())) {
                execution.setRequirement("DISABLED");
                getAdminClient().realm(realmName).flows().updateExecutions(flowAlias, execution);
            }
        }
    }
}
