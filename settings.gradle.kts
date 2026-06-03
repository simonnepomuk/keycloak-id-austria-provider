plugins {
    id("com.mooltiverse.oss.nyx") version "3.1.7"
}

nyx {
  preset = "simple"
  initialVersion = "0.1.0"
}

rootProject.name = "keycloak-id-austria-provider"
include("lib")
