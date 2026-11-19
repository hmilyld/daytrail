import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spotless)
}

/** 读取 local.properties（不进入 Git），用于 SDK 路径与高德 Key。 */
val localProperties: Properties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

/** 读取 keystore.properties（不进入 Git），用于 Release 签名。 */
val keystoreProperties: Properties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

/**
 * 高德 Key 按 buildType 注入。缺失时退回空串，让工程先能构建，
 * 运行时地图会显示空白网格而不是崩溃。
 */
fun amapKey(propertyName: String): String = localProperties.getProperty(propertyName, "").trim()

android {
    namespace = "com.hmilyld.daytrail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hmilyld.daytrail"
        minSdk = 31
        targetSdk = 37
        versionCode = providers.gradleProperty("app.versionCode").get().toInt()
        versionName = providers.gradleProperty("app.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // 轨迹纠偏走 Web 服务 API，用的是「Web 服务」类型的 Key，
        // 和地图 SDK 的 Android Key 不是一回事。缺失时为空串，
        // 界面会把「补全路线」按钮禁用。
        buildConfigField("String", "AMAP_WEB_KEY", "\"${amapKey("AMAP_WEB_KEY")}\"")
    }

    signingConfigs {
        // 覆盖 AGP 自动生成的 debug 签名，指向仓库内固定的 debug.jks，
        // 保证任何机器上 debug SHA1 一致，不用重复申请高德 Key。
        getByName("debug") {
            storeFile = file("keystore/debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["amapApiKey"] = amapKey("AMAP_KEY_DEBUG")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            manifestPlaceholders["amapApiKey"] = amapKey("AMAP_KEY_RELEASE")
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources.excludes +=
            setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
            )
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Compose 的 @Preview 与资源相关的单测需要编译后的 Android 资源
        unitTests.isIncludeAndroidResources = true
    }
}

/**
 * Kotlin 2.3 已移除 android.kotlinOptions，统一用 compilerOptions DSL。
 * jvmTarget 默认取 compileOptions.targetCompatibility，这里显式声明以免隐式行为。
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * AGP 9 默认只给 debug 变体创建 host test（本地单元测试）。
 * Release 变体也跑一遍单测，确保 R8 开启后逻辑没有差异。
 * 同时 Compose Preview 的布局渲染需要 host test 打包资源，缺了会直接构建失败。
 */
androidComponents {
    beforeVariants { variantBuilder ->
        variantBuilder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.enable = true
    }
}

/**
 * ktlint 官方风格（与 kotlin.code.style=official 一致）+ Google 的 100 列限制。
 * Compose 的 PascalCase 可组合函数名通过注解豁免。
 */
spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("build/**/*.kt")
        ktlint("1.5.0").editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "ktlint_official",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "max_line_length" to "100",
            ),
        )
    }
    kotlinGradle {
        target("*.kts")
        ktlint("1.5.0")
    }
}

/**
 * Room 的 schema 导出位置。
 *
 * 必须导出：轨迹是用户长期积累的数据，后续任何表结构变更都需要基于
 * schema 历史写 Migration，没有历史就只能猜。
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.adaptive.navigation3)
    implementation(libs.androidx.adaptive.layout)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // 高德地图 SDK。10.0.x 起已内置定位 SDK（AMapLocationClient 等），
    // 因此不能再单独引入 com.amap.api:location，否则会出现重复类。
    implementation(libs.amap.map)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
