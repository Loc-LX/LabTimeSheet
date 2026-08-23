import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (path) => readFileSync(resolve(root, path), "utf8");
const expectedActionPins = new Map([
  ["actions/checkout", "3d3c42e5aac5ba805825da76410c181273ba90b1"],
  ["actions/setup-java", "b6effb05e454b25005698d916606bdc6ffcbf961"],
  ["actions/setup-node", "820762786026740c76f36085b0efc47a31fe5020"],
  ["docker/setup-buildx-action", "bb05f3f5519dd87d3ba754cc423b652a5edd6d2c"],
  ["docker/login-action", "dbcb813823bdd20940b903addbd779551569679f"],
  ["docker/build-push-action", "53b7df96c91f9c12dcc8a07bcb9ccacbed38856a"],
]);

test("production image and Compose keep one image usable with bundled or external PostgreSQL", () => {
  const dockerfile = read("Dockerfile");
  const compose = read("compose.yaml");
  const environment = read(".env.compose.example");
  const production = read("src/main/resources/application-prod.yaml");

  assert.match(dockerfile, /FROM node:24-alpine@sha256:/);
  assert.match(dockerfile, /FROM eclipse-temurin:25-jre-alpine@sha256:/);
  assert.match(dockerfile, /USER 10001:10001/);
  assert.match(dockerfile, /HEALTHCHECK .*health\/readiness/);
  assert.match(compose, /profiles: \["bundled-db"\]/);
  assert.match(compose, /condition: service_healthy/);
  assert.match(compose, /required: false/);
  assert.match(compose, /postgres_data:\s*$/m);
  assert.match(environment, /^LAB_DB_URL=/m);
  assert.match(environment, /^LAB_IMAGE=/m);
  assert.match(production, /same-site: strict/);
  assert.match(production, /include: "readinessState,db"/);
});

test("verification workflow checks every pull request and pushed branch without write permission", () => {
  const workflow = read(".gitea/workflows/verify.yml");

  assert.match(workflow, /pull_request:/);
  assert.match(workflow, /push:/);
  assert.match(workflow, /contents: read/);
  assert.match(workflow, /npm ci/);
  assert.match(workflow, /npm run test:ui/);
  assert.match(workflow, /npm run build/);
  assert.match(workflow, /\.\/mvnw -B test/);
  assert.match(workflow, /TESTCONTAINERS_HOST_OVERRIDE: host\.docker\.internal/);
  assert.doesNotMatch(workflow, /permissions:\s*write-all/);
});

test("container workflow runs only manually or on main and verifies before either image build", () => {
  const workflow = read(".gitea/workflows/container.yml");

  assert.match(workflow, /'on':\n  workflow_dispatch:\n  push:\n    branches:\n      - main/);
  assert.doesNotMatch(workflow, /^  pull_request:/m);
  assert.match(workflow, /jobs:\n  verify:/);
  assert.match(workflow, /amd64:\n    needs: verify/);
  assert.match(workflow, /arm64:[\s\S]*?needs: verify/);
  assert.match(workflow, /TESTCONTAINERS_HOST_OVERRIDE: host\.docker\.internal/);
  assert.match(workflow, /runs-on: ubuntu-latest-arm/);
  assert.match(workflow, /vars\.ARM64_RUNNER_AVAILABLE == 'true'/);
  assert.match(workflow, /gitea\.ref == 'refs\/heads\/main'/);
  assert.match(workflow, /sha-\$\{GITEA_SHA\}-amd64/);
  assert.match(workflow, /sha-\$\{GITEA_SHA\}-arm64/);
  assert.match(workflow, /imagetools create/);
  assert.match(workflow, /REGISTRY: git\.sechmachine\.io\.vn/);
  assert.match(workflow, /IMAGE_NAME: sechmachine\/labtimesheet/);
  assert.match(workflow, /username: \$\{\{ gitea\.actor \}\}/);
  assert.match(workflow, /password: \$\{\{ secrets\.REGISTRY_TOKEN \}\}/);
  assert.doesNotMatch(workflow, /CONTAINER_IMAGE|REGISTRY_USERNAME/);
});

test("deployment template is dormant, main-only, host-key checked, and rollback-capable", () => {
  const workflow = read(".gitea/workflows/container.yml");

  assert.match(
    workflow,
    /deploy:\n\s+if: gitea\.ref == 'refs\/heads\/main' && vars\.DEPLOY_ENABLED == 'true'/,
  );
  assert.match(workflow, /secrets\.DEPLOY_HOST != ''/);
  assert.match(workflow, /secrets\.DEPLOY_USER != ''/);
  assert.match(workflow, /secrets\.DEPLOY_PRIVATE_KEY != ''/);
  assert.match(workflow, /secrets\.DEPLOY_KNOWN_HOSTS != ''/);
  assert.match(workflow, /StrictHostKeyChecking=yes/);
  assert.match(workflow, /UserKnownHostsFile/);
  assert.match(workflow, /docker pull/);
  assert.match(workflow, /docker compose/);
  assert.match(workflow, /State\.Health\.Status/);
  assert.match(workflow, /previous-image/);
  assert.match(workflow, /rollback/i);
  assert.doesNotMatch(workflow, /jobs\.[a-z0-9_-]+\.environment/);
});

test("enabled deployment passes one absolute remote Compose file through rollout, health, and rollback", () => {
  const workflow = read(".gitea/workflows/container.yml");
  const deployment = read("DEPLOYMENT.md");

  assert.match(workflow, /REMOTE_COMPOSE_FILE: \/etc\/labtimesheet\/compose\.yaml/);
  assert.match(workflow, /bash -s -- "\$REMOTE_COMPOSE_ENV" "\$REMOTE_COMPOSE_FILE" "\$IMAGE"/);
  assert.match(workflow, /compose_file=\$2/);
  assert.match(workflow, /test -f "\$compose_file"/);
  assert.match(workflow, /docker compose --env-file "\$compose_env" -f "\$compose_file"/);
  assert.match(deployment, /\/etc\/labtimesheet\/compose\.yaml/);
});

test("deterministic E2E clock is opt-in and rejected in production", () => {
  const e2e = read("src/main/resources/application-e2e.yaml");
  const application = read("src/main/resources/application.yaml");
  const timeConfiguration = read("src/main/java/com/lab/labtimesheet/config/TimeConfiguration.java");
  const development = read("DEVELOPMENT.md");
  const testing = read("TESTING.md");
  const startupTest = read("src/test/java/com/lab/labtimesheet/config/E2eProfileIntegrationTest.java");

  assert.match(e2e, /on-profile: e2e/);
  assert.match(e2e, /start-instant: "\$\{LAB_E2E_START_INSTANT:/);
  assert.match(application, /group:\n\s+e2e: dev/);
  assert.match(timeConfiguration, /@Profile\("e2e & !prod"\)/);
  assert.match(timeConfiguration, /@Profile\("prod & e2e"\)/);
  assert.match(timeConfiguration, /e2e fixed clock cannot be enabled with prod/);
  assert.match(timeConfiguration, /lab\.e2e\.fixed-instant is retired; use lab\.e2e\.start-instant/);
  assert.match(startupTest, /@SpringBootTest/);
  assert.match(startupTest, /@ActiveProfiles\("e2e"\)/);
  assert.match(development, /LAB_E2E_START_INSTANT=.*\n\s+\.\/mvnw spring-boot:run/);
  assert.match(testing, /LAB_E2E_START_INSTANT=.*spring-boot:run/s);
  assert.doesNotMatch(development, /spring\.profiles\.active=prod,e2e/);
});

test("workflows pin every action to the latest reviewed immutable release", () => {
  const workflows = [
    read(".gitea/workflows/verify.yml"),
    read(".gitea/workflows/container.yml"),
  ].join("\n");
  const uses = [...workflows.matchAll(/uses:\s+([^@\s]+)@([0-9a-f]{40})/g)];

  assert.ok(uses.length > 0);
  for (const [, action, pin] of uses) {
    assert.equal(pin, expectedActionPins.get(action), `unexpected pin for ${action}`);
  }
  assert.deepEqual(new Set(uses.map(([, action]) => action)), new Set(expectedActionPins.keys()));
  assert.doesNotMatch(workflows, /uses:\s+[^\s]+@v\d/);
});
