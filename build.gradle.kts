// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// kotlin-stdlib-jdk7/jdk8 were merged into kotlin-stdlib in Kotlin 1.8;
// use an explicit version because BOM-managed constraints carry no version string.
val kotlinVersion = "2.2.10"
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" &&
                (requested.name == "kotlin-stdlib-jdk7" || requested.name == "kotlin-stdlib-jdk8")
            ) {
                useTarget("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
                because("jdk7/jdk8 variants merged into kotlin-stdlib since Kotlin 1.8")
            }
        }
    }
}
