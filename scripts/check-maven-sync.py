#!/usr/bin/env python3
"""Checks that the Maven build (pom.xml files) matches the Gradle build (*.gradle.kts).

Compares coordinates, modules, external dependency versions, Java versions and the
classifier's main class. Run from the repository root; exits 1 and lists every
difference when the builds disagree.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ROOT = Path(__file__).resolve().parent.parent

problems = []


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def pom(path):
    return ET.parse(ROOT / path).getroot()


def text(element, path):
    found = element.find(path, NS)
    return found.text.strip() if found is not None and found.text else None


def check(what, gradle, maven):
    if gradle != maven:
        problems.append(f"{what}: Gradle has {gradle!r}, Maven has {maven!r}")


root_gradle = read("build.gradle.kts")
root_pom = pom("pom.xml")
group = re.search(r'group\s*=\s*"([^"]+)"', root_gradle).group(1)
version = re.search(r'version\s*=\s*"([^"]+)"', root_gradle).group(1)
check("groupId", group, text(root_pom, "m:groupId"))
check("version", version, text(root_pom, "m:version"))

# Each Gradle project is a Maven module of the same name. The probe has a second module,
# probe/tests, because its tests use newer library versions than its main code compiles against.
gradle_projects = re.findall(r'include\("([^"]+)"\)', read("settings.gradle.kts"))
module_poms = {}
for project in gradle_projects:
    poms = [f"{project}/pom.xml"] + ([f"{project}/tests/pom.xml"] if (ROOT / project / "tests/pom.xml").exists() else [])
    module_poms[project] = poms
maven_modules = sorted(text(m, ".") for m in root_pom.findall("m:modules/m:module", NS))
expected_modules = sorted(str(Path(p).parent) for poms in module_poms.values() for p in poms)
check("modules", expected_modules, maven_modules)

java_version = None
for project, poms in module_poms.items():
    gradle = read(f"{project}/build.gradle.kts")
    models = [pom(p) for p in poms if (ROOT / p).exists()]
    if len(models) != len(poms):
        problems.append(f"{project}: missing {[p for p in poms if not (ROOT / p).exists()]}")
        continue

    for p, model in zip(poms, models):
        check(f"{p} parent version", version, text(model, "m:parent/m:version"))

    gradle_deps = {
        f"{g}:{a}:{v}"
        for g, a, v in re.findall(r'"([\w.\-]+):([\w.\-]+):([\w.\-]+)"', gradle)
        if g != group
    }
    maven_deps = {
        f"{text(d, 'm:groupId')}:{text(d, 'm:artifactId')}:{text(d, 'm:version')}"
        for model in models
        for d in model.findall("m:dependencies/m:dependency", NS)
        if text(d, "m:groupId") != group and not text(d, "m:groupId").startswith("${")
    }
    for dep in sorted(gradle_deps - maven_deps):
        problems.append(f"{project}: {dep} is in the Gradle build but not in {', '.join(poms)}")
    for dep in sorted(maven_deps - gradle_deps):
        problems.append(f"{project}: {dep} is in {', '.join(poms)} but not in the Gradle build")

    toolchain = re.search(r"JavaLanguageVersion\.of\((\d+)\)", gradle)
    if toolchain:
        if java_version is None:
            java_version = toolchain.group(1)
        check(f"{project} Java toolchain (all modules share maven.compiler.release)", java_version, toolchain.group(1))
    release = re.search(r"options\.release\s*=\s*(\d+)", gradle)
    if release:
        check(f"{project} main code release", release.group(1), text(models[0], "m:properties/m:maven.compiler.release"))

    main_class = re.search(r'mainClass\.set\("([^"]+)"\)', gradle)
    if main_class:
        check(f"{project} main class", main_class.group(1), text(models[0], "m:properties/m:exec.mainClass"))

check("Java version", java_version, text(root_pom, "m:properties/m:maven.compiler.release"))

if problems:
    print("The Maven build is out of sync with the Gradle build:")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)
print(f"Maven build matches the Gradle build ({len(gradle_projects)} projects).")
