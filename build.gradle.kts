plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "io.github.rafaelrabeloit"
version = "0.1.0"

kotlin {
    jvm()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/rafaelrabeloit/bitfield-parser")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("BitField Parser")
            description.set("A Kotlin Multiplatform library for declarative bitfield schema definition and parsing")
            url.set("https://github.com/rafaelrabeloit/bitfield-parser")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("rafaelrabeloit")
                    name.set("Rafael")
                }
            }
            scm {
                url.set("https://github.com/rafaelrabeloit/bitfield-parser")
                connection.set("scm:git:git://github.com/rafaelrabeloit/bitfield-parser.git")
                developerConnection.set("scm:git:ssh://github.com/rafaelrabeloit/bitfield-parser.git")
            }
        }
    }
}
