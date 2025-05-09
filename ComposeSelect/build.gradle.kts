plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.example.composeselect"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"

    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
   // implementation("com.google.android.material:material:1.12.0")
    implementation ("androidx.compose.foundation:foundation:1.7.3")
    implementation ("androidx.compose.runtime:runtime-livedata:1.7.3")
    implementation ("androidx.compose.ui:ui:1.7.3")
    implementation("androidx.compose.material3:material3:1.2.1")
//    implementation ("androidx.compose.material:material:1.7.3")
//    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.chirantandeveloper"
                artifactId = "ComposeSelect"
                version = "1.0"
            }
        }
    }
}