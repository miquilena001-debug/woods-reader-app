
import sys

with open("app/build.gradle.kts", "r", encoding="utf-8") as f:
    lines = f.readlines()

plugins_end_idx = 0
for i, line in enumerate(lines):
    if "plugins {" in line:
        plugins_end_idx = i
    if "}" in line and plugins_end_idx != 0:
        plugins_end_idx = i
        break

lines.insert(plugins_end_idx, """
    id("io.gitlab.arturbosch.detekt") version "1.23.1"
    id("org.jetbrains.kotlinx.kover") version "0.7.3"
    id("info.solidsoft.pitest") version "1.9.0"
""")

# add config at the end
lines.append("""
detekt {
    buildUponDefaultConfig = true
    allRules = true
}

koverReport {
    filters {
        excludes {
            classes("*.BuildConfig", "*.*_Factory*", "*.*_HiltModules*", "*.*_Impl*")
        }
    }
}

pitest {
    targetClasses.set(setOf("com.example.woodsreader.*"))
    pitestVersion.set("1.9.0")
    threads.set(4)
    outputFormats.set(setOf("XML", "HTML"))
}
""")

with open("app/build.gradle.kts", "w", encoding="utf-8") as f:
    f.writelines(lines)

