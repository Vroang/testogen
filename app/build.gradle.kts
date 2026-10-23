import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.testogen.app"
    compileSdk = 37

    defaultConfig {
        // applicationId и подпись релиза не менять: обновление ставится поверх старой версии
        applicationId = "com.testogen.app"
        minSdk = 26
        targetSdk = 37
        // versionCode растёт монотонно; тег релиза v<versionCode> (старая ветка закончилась на 49)
        versionCode = 50
        versionName = "2.0"
    }

    // Подпись релиза: на CI параметры приходят из GitHub Secrets. Локально без них release
    // собирается неподписанным — только чтобы проверить R8 и размер.
    // Пустая строка — секрета нет (так GitHub подставляет отсутствующий секрет)
    val hasReleaseKey = !System.getenv("KEYSTORE_PASSWORD").isNullOrEmpty()
    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            storeFile = file(storePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        // DEV_TOOLS — служебные возможности для проверки разработчиком (показать праздничный экран ещё раз);
        // в сборке для мамы выключены
        debug {
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        release {
            buildConfigField("boolean", "DEV_TOOLS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
        // Как release (R8, без отладки), но с debug-подписью — для замеров плавности
        // на телефоне и для Baseline Profile. Маме не отдаётся.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("boolean", "DEV_TOOLS", "true")
        }
        // Шаг 6: как benchmark, но без R8 — на нём записывается Baseline Profile с настоящими
        // именами классов (tools/baseline_profile.sh). Готовый профиль — src/main/baseline-prof.txt.
        create("profiling") {
            initWith(getByName("benchmark"))
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // BouncyCastle (зависимость pdfbox): таблицы постквантовой криптографии — ~8 МБ, PDF они не нужны
        resources.excludes += "org/bouncycastle/pqc/**"
    }

    androidResources {
        // Таблицы китайских, японских и корейских кодировок pdfbox — русским учебникам не нужны
        // (Identity-H/V остаются). Первая часть — стандартный список исключений.
        ignoreAssetsPatterns += listOf(
            "!.svn", "!.git", "!.ds_store", "!*.scc", ".*", "<dir>_*", "!CVS", "!thumbs.db", "!picasa.ini", "!*~",
            "!83pv-*", "!90ms*", "!90pv-*", "!Add-*", "!Adobe-*", "!B5pc-*", "!CNS-*", "!ETen*", "!EUC-*", "!Ext-*",
            "!GB*", "!HKscs-*", "!KSC*", "!Uni*", "!H", "!V",
        )
    }
}

// Схема базы сохраняется в app/schemas — нужна для будущих миграций
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.pdfbox.android)
    // Ставит Baseline Profile при установке не из Play (APK с GitHub) — быстрее запуск и прокрутка
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}
