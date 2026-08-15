# Test Evidence: Platform Lombok boilerplate retrofit

- **Test type:** Unit
- **Requirement IDs:** `Engineering policy — targeted Lombok retrofit`
- **Scenario IDs:** `Source-audit RED/GREEN`
- **Test class/method:** `N/A — reproducible source audit; behavior is covered by the affected suites below`
- **Implementation commit:** `41448903aa924dc5852db8d7bb4d9319cb9f91a7`

## Protected behavior

Platform-owned Spring collaborators, request forms, and JPA entities must not retain eligible handwritten
dependency-assignment constructors, trivial accessors, or empty persistence constructors. The retrofit must preserve
constructor visibility, form normalization, entity encapsulation, defensive copies of credential/token bytes, and all
account, authentication, SMTP, and cross-feature behavior.

## Test method

The source audit searches only the 79 members classified as mechanical after reading every root/config,
`feature.account`, and `feature.integration` production type. It deliberately excludes normalized email/display-name
setters, defensive byte-array getters, domain constructors and factories, state transitions, the normalized
`AccountService` public-origin constructor, `SecretCipher` key construction, and the two-constructor JavaMail test
seam. No permanent annotation-presence test was added because annotations are an implementation detail; compilation
and production-shaped tests protect the real contracts.

## Hand-derived expected result

Before the retrofit the audit must find 79 eligible handwritten members and exit 1. After targeted Lombok generation,
the same audit must find none and exit 0, while the retained non-mechanical members remain explicit.

## RED

**Command**

```text
matches=$( { rg -n '^    (public )?(AccountController|BootstrapAccessFilter|BootstrapController|BootstrapService|DatabaseUserDetailsService|SmtpController|SmtpWarningAdvice|MailDeliveryService|SmtpConfigurationService)\(' src/main/java/com/lab/labtimesheet/feature/account src/main/java/com/lab/labtimesheet/feature/integration; rg -n '^    public (String getMasterKey|void setMasterKey)\(' src/main/java/com/lab/labtimesheet/config/SecurityProperties.java; rg -n '^    public (String get(Token|Password|ConfirmPassword)|void set(Token|Password|ConfirmPassword))\(' src/main/java/com/lab/labtimesheet/feature/account/model/dto/ActivationForm.java; rg -n '^    public (String get(Email|DisplayName|Password)|void setPassword)\(' src/main/java/com/lab/labtimesheet/feature/account/model/dto/BootstrapForm.java; rg -n '^    public .+ (get(Email|DisplayName|Role|StudentCode|InternshipStart|InternshipEnd)|set(Role|StudentCode|InternshipStart|InternshipEnd))\(' src/main/java/com/lab/labtimesheet/feature/account/model/dto/CreateAccountForm.java; rg -n '^    public (Long getDraftId|void setDraftId)\(' src/main/java/com/lab/labtimesheet/feature/integration/model/dto/SmtpActionForm.java; rg -n '^    public .+ (get(Host|Port|SecurityMode|Username|Password|FromAddress|FromName)|set(Host|Port|SecurityMode|Username|Password|FromAddress|FromName))\(' src/main/java/com/lab/labtimesheet/feature/integration/model/dto/SmtpForm.java; rg -n '^    protected (AppUser|InternProfile|SystemState|UserActionToken|SmtpConfiguration)\(\)' src/main/java/com/lab/labtimesheet/feature/account/model/entity src/main/java/com/lab/labtimesheet/feature/integration/model/entity; rg -n '^    public .+ (get(Id|Email|DisplayName|PasswordHash|GlobalRole|AccountStatus|ActivatedAt|InternshipStatus|InternshipStartDate|InternshipEndDate|UserId|Purpose|ExpiresAt|UsedAt|InvalidatedAt|Status|Host|Port|SecurityMode|Username|SecretKeyVersion|FromAddress|FromName|TestedAt)|isInitialized)\(' src/main/java/com/lab/labtimesheet/feature/account/model/entity src/main/java/com/lab/labtimesheet/feature/integration/model/entity; } ); if [ -n "$matches" ]; then printf '%s\n' "$matches"; printf 'RED: eligible handwritten Lombok boilerplate remains (%s matches)\n' "$(printf '%s\n' "$matches" | wc -l | tr -d ' ')"; exit 1; fi; printf 'GREEN: no eligible handwritten Lombok boilerplate remains\n'
```

**Observed result**

```text
RED: eligible handwritten Lombok boilerplate remains (79 matches)
Process exited with code 1 because the confirmed mechanical members were still handwritten.
```

## GREEN

**Command**

```text
The exact RED source-audit command above was repeated without alteration.
```

**Observed result**

```text
GREEN: no eligible handwritten Lombok boilerplate remains
Process exited with code 0.
```

## Affected suite

**Command and result**

```text
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -DskipTests compile
BUILD SUCCESS — 127 production source files compiled on Java 25.

export DOCKER_HOST=unix:///Users/sechmachine/.orbstack/run/docker.sock
./mvnw -Dtest=LabtimesheetApplicationTests,LayerStructureTest,PlatformFoundationTest,TimeConfigurationTest,SecurityResponseIntegrationTest,AccountWebIntegrationTest,AuthenticationWebIntegrationTest,BootstrapOnboardingWebIntegrationTest,AccountActivationIntegrationTest,BootstrapIntegrationTest,SmtpOnboardingWebIntegrationTest,JavaMailSmtpProbeTest,SmtpIntegrationTest test
BUILD SUCCESS — Tests run: 29, Failures: 0, Errors: 0, Skipped: 0; PostgreSQL 18.4.

./mvnw test
BUILD SUCCESS — Tests run: 197, Failures: 0, Errors: 0, Skipped: 0; PostgreSQL 18.4.

./mvnw -DskipTests -Ddoclint=all javadoc:javadoc
BUILD SUCCESS — doclint reported no errors; Maven emitted 66 non-fatal missing-comment warnings across the integrated
tree, including generated default constructors/accessors.

git diff --check
No output; exit 0.
```

## External-test boundaries

This source audit does not prove Lombok internals or enforce a preferred annotation spelling. The compile and
PostgreSQL-backed affected/full suites prove generated constructor/accessor compatibility with Spring binding,
Security, JPA/Hibernate, Thymeleaf, and existing cross-feature consumers. No dependency, schema, migration, token,
credential, template, container, CI, or runtime configuration was changed.
