import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (path) => readFileSync(resolve(root, path), "utf8");

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
  assert.doesNotMatch(workflow, /permissions:\s*write-all/);
});

test("container workflow publishes only main and makes native ARM64 explicitly optional", () => {
  const workflow = read(".gitea/workflows/container.yml");

  assert.match(workflow, /runs-on: ubuntu-latest-arm/);
  assert.match(workflow, /vars\.ARM64_RUNNER_AVAILABLE == 'true'/);
  assert.match(workflow, /gitea\.ref == 'refs\/heads\/main'/);
  assert.match(workflow, /sha-\$\{GITEA_SHA\}-amd64/);
  assert.match(workflow, /sha-\$\{GITEA_SHA\}-arm64/);
  assert.match(workflow, /imagetools create/);
  assert.doesNotMatch(workflow, /ssh|DEPLOY_HOST|DEPLOY_KEY/i);
});
