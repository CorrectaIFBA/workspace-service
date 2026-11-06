rootProject.name = "workspace-service"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

  repositories {
    mavenCentral()
  }
}

buildCache {
  local {
    isEnabled = true
  }
}