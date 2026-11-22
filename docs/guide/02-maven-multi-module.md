# 02 — Maven Multi-Module Setup

## Files
- `pom.xml` (parent, `packaging=pom`): lists modules, fixes Java 21, pins every library version once
  in `<dependencyManagement>` and every plugin version in `<pluginManagement>`.
- `automation-core/pom.xml`: the library. Declares dependencies *without versions* (inherited from parent).
- `parabank-tests/pom.xml`: depends on `automation-core` like on any other library.

## Logging
Log4j2. `automation-core` depends only on `log4j-api` (a library should not choose the logging engine for
its users). Tests add `log4j-core` and `log4j-slf4j2-impl`, which also captures Selenium's SLF4J logs.

## Why a parent POM?
One place to upgrade Selenium or POI. Children can't drift to different versions.

## What `mvn package` produces for the core
- `automation-core-1.0.0-SNAPSHOT.jar` — compiled code
- `-sources.jar` — source code (IDEs show it when you Ctrl-click)
- `-javadoc.jar` — API documentation
These three files plus the POM are exactly what gets uploaded to JFrog Artifactory with `mvn deploy`.
Other teams then add one `<dependency>` block — they never copy JAR files by hand.

## `distributionManagement`
Tells `mvn deploy` *where* to upload: releases (`1.0.0`) and snapshots (`1.0.0-SNAPSHOT`) go to different
repositories. Username/password are **not** in the POM — they live in `~/.m2/settings.xml` under the
matching `<server><id>jfrog-releases</id>…` (set up in Plan 05).

## SNAPSHOT vs release
`-SNAPSHOT` = work in progress, can be overwritten. A release (`1.0.0`) is immutable.

## Useful commands
| Command | Does |
|---|---|
| `mvn clean package -DskipTests` | Build all modules without tests |
| `mvn -pl automation-core test` | Unit tests of the core only |
| `mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none` | Real-browser tests of the core |
| `mvn versions:display-dependency-updates` | Show newer library versions |
