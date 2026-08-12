# Lab Timesheet

Lab timesheet / attendance tracking web application built with Java Servlets (Jakarta EE), JSP, and Apache Tomcat 10.1.

## Quick start
- Build: `.\mvnw.cmd -q clean package` (Windows) or `./mvnw clean package` (macOS/Linux) → produces `target/lab-timesheet.war`
- Deploy: copy `target/lab-timesheet.war` into your Tomcat 10.1 `webapps/` folder and start Tomcat (`bin/startup.bat` on Windows)
- Open http://localhost:8080/lab-timesheet/ (rename the WAR to `ROOT.war` to serve at `/`)
- No Maven install required — the Maven Wrapper (`mvnw`) is committed to the repo and pins Maven 3.9.x

## Prerequisites
- Java 17 (Temurin/OpenJDK 17 recommended)
- Git
- Apache Tomcat 10.1.x (https://tomcat.apache.org/) — download and unzip it
- Device: IntelliJ IDEA recommended — open `pom.xml` and let it load the Maven project

You do **not** need to install Maven. The wrapper downloads the pinned Maven version on first run.

## Running from the IDE
- IntelliJ: `File → Open` the project root, wait for Maven import, then register your Tomcat 10.1 install (`Settings → Build, Execution, Deployment → Application Servers`) and create a `Tomcat Server` run configuration that deploys the WAR/exploded WAR.

## Project structure
Java sources live under `src/main/java/com/group1/timesheet/`:

```
com.group1.timesheet
├── auth/                  # login, sessions, user accounts
│   ├── controller/        # servlets / request handlers
│   ├── service/           # business logic
│   ├── dao/               # data access
│   └── model/             # entities / DTOs
├── intern/                # intern records
├── attendance/            # attendance & timesheet tracking
├── report/                # reporting
├── configuration/         # admin / system configuration
├── notification/          # notifications
└── common/
    ├── config/            # shared configuration
    ├── filter/            # servlet filters (auth, encoding, ...)
    ├── exception/         # custom exceptions
    └── util/              # helpers / utilities
```

Web assets and JSPs live under `src/main/webapp/` (templates go in `WEB-INF/`).

## Code conventions (by module)
- Each feature module (`auth`, `intern`, `attendance`, `report`, `configuration`, `notification`) owns its `controller/`, `service/`, `dao/`, and `model/` packages — no cross-module randomness
- Controllers are Java Servlets (`@WebServlet` or `web.xml` mappings) that delegate to services
- Cross-cutting concerns (auth filters, shared exceptions, utilities, app config) live in `common/` and are reusable across modules
- JSPs render server-side; business logic stays out of views

*Conventions will be finalized as the first feature (auth) lands — keep new code aligned with the module above.*

## Directory orientation
- All feature packages are empty scaffolds (directories only, no code yet)
- `src/main/webapp` contains a placeholder `index.jsp`
- No tests yet — add unit tests per module as features are built
- No CI / CI server configured

## Deployment
- `.\mvnw.cmd package` produces `target/lab-timesheet.war`
- Drop the WAR into a Tomcat 10.1.x `webapps/` folder and start Tomcat — the app is served at `http://localhost:8080/lab-timesheet/` (rename to `ROOT.war` for context `/`)