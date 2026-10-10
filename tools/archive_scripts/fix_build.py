
import sys

with open("app/build.gradle.kts", "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if line.strip().startswith("id(\"io.gitlab.arturbosch.detekt\")") or \
       line.strip().startswith("id(\"org.jetbrains.kotlinx.kover\")") or \
       line.strip().startswith("id(\"info.solidsoft.pitest\")"):
        continue
    
    if line.startswith("detekt {"):
        skip = True
    if line.startswith("koverReport {"):
        skip = True
    if line.startswith("pitest {"):
        skip = True
        
    if skip:
        if line.strip() == "}":
            skip = False
        continue
        
    new_lines.append(line)

with open("app/build.gradle.kts", "w", encoding="utf-8") as f:
    f.writelines(new_lines)

