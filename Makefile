# ERMS developer entry points. Run `make` (or `make help`) for the list.
SHELL := /bin/bash
.DEFAULT_GOAL := help
MAKEFLAGS += --no-print-directory

# Rancher Desktop installs docker/kubectl/helm in ~/.rd/bin.
export PATH := $(HOME)/.rd/bin:$(PATH)

KUBE_CONTEXT    ?= rancher-desktop
NAMESPACE       ?= erms
RELEASE         ?= erms
CHART           := deploy/helm/erms
BASE_URL        ?= http://erms.localhost
IMAGE_REGISTRY  ?= ghcr.io/manojis
IMAGE_TAG       ?= dev
K8S_VERSION     ?= 1.36.0

KUBECONFORM ?= docker run --rm -i ghcr.io/yannh/kubeconform:v0.8.0
ACTIONLINT  ?= docker run --rm -v "$(CURDIR)":/repo -w /repo rhysd/actionlint:1.7.12
SHELLCHECK  ?= docker run --rm -v "$(CURDIR)":/mnt -w /mnt koalaman/shellcheck:v0.11.0
HADOLINT    ?= docker run --rm -i hadolint/hadolint:v2.15.1 hadolint --failure-threshold warning -

# Testcontainers on Rancher Desktop (docs/TESTING.md).
RD_SOCK := $(HOME)/.rd/docker.sock
ifneq ($(wildcard $(RD_SOCK)),)
TC_ENV := DOCKER_HOST=unix://$(RD_SOCK) TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
endif

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make <target> [VAR=value]\n"} \
	  /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5) } \
	  /^[a-zA-Z0-9_-]+:.*##/ { printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Backend
.PHONY: be-test be-build be-lint be-format
be-test: ## Backend: ./mvnw verify (Spotless, unit + Testcontainers ITs, JaCoCo gates)
	cd backend && $(TC_ENV) ./mvnw -B -ntp verify

be-build: ## Backend: package the jar without tests
	cd backend && ./mvnw -B -ntp -DskipTests package

be-lint: ## Backend: Spotless check
	cd backend && ./mvnw -B -ntp spotless:check

be-format: ## Backend: Spotless apply
	cd backend && ./mvnw -B -ntp spotless:apply

##@ Frontend
.PHONY: fe-install fe-test fe-build fe-lint fe-format
fe-install: ## Frontend: npm ci
	cd frontend && npm ci

fe-test: fe-install ## Frontend: lint, format check, typecheck, tests with coverage
	cd frontend && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage

fe-build: fe-install ## Frontend: production build
	cd frontend && npm run build

fe-lint: fe-install ## Frontend: ESLint + Prettier check + tsc
	cd frontend && npm run lint && npm run format:check && npm run typecheck

fe-format: ## Frontend: Prettier write
	cd frontend && npm run format

##@ Aggregates
.PHONY: test build lint
test: be-test fe-test ## Run backend and frontend test suites

build: be-build fe-build ## Build backend jar and frontend bundle

lint: be-lint fe-lint helm-lint sh-lint actions-lint docker-lint ## All linters (code, chart, scripts, workflows, Dockerfiles)

##@ Platform lint
.PHONY: helm-lint helm-template sh-lint actions-lint docker-lint
helm-lint: ## helm lint (local + default values) and kubeconform -strict on the rendered chart
	helm lint $(CHART) -f $(CHART)/values-local.yaml --strict
	helm lint $(CHART) --strict
	set -o pipefail; helm template $(RELEASE) $(CHART) -n $(NAMESPACE) -f $(CHART)/values-local.yaml \
	  --set backend.image.tag=ci --set frontend.image.tag=ci \
	  | $(KUBECONFORM) -strict -summary -kubernetes-version $(K8S_VERSION) -

helm-template: ## Render the chart with local values to stdout
	@helm template $(RELEASE) $(CHART) -n $(NAMESPACE) -f $(CHART)/values-local.yaml \
	  --set backend.image.tag=$(IMAGE_TAG) --set frontend.image.tag=$(IMAGE_TAG)

sh-lint: ## shellcheck scripts/
	$(SHELLCHECK) -x scripts/*.sh

actions-lint: ## actionlint .github/workflows
	$(ACTIONLINT) -color

docker-lint: ## hadolint both Dockerfiles
	$(HADOLINT) < backend/Dockerfile
	$(HADOLINT) < frontend/Dockerfile

##@ docker-compose (no Kubernetes)
.PHONY: up down logs ps
up: ## Build and start mysql + backend + frontend (app at http://localhost:3000)
	docker compose up --build -d --wait
	@echo "App: http://localhost:3000  API: http://localhost:8080/api/v1  Swagger: http://localhost:8080/api/docs"

down: ## Stop the compose stack (keeps the DB volume; `docker compose down -v` wipes it)
	docker compose down

logs: ## Follow compose logs
	docker compose logs -f

ps: ## Compose service status
	docker compose ps

##@ Images
.PHONY: images
images: ## Build both images locally as $(IMAGE_REGISTRY)/erms-{backend,frontend}:$(IMAGE_TAG)
	docker build -t $(IMAGE_REGISTRY)/erms-backend:$(IMAGE_TAG) backend
	docker build -t $(IMAGE_REGISTRY)/erms-frontend:$(IMAGE_TAG) frontend

##@ Rancher Desktop (Kubernetes)
.PHONY: deploy-local smoke status undeploy-local
deploy-local: ## Build images, helm upgrade --install to rancher-desktop, helm test, smoke test
	scripts/deploy-local.sh --context $(KUBE_CONTEXT) --namespace $(NAMESPACE)

smoke: ## Smoke test BASE_URL (default http://erms.localhost; compose: make smoke BASE_URL=http://localhost:3000)
	scripts/smoke-test.sh $(BASE_URL)

status: ## Release, pods, services, ingress and PVC status
	helm status $(RELEASE) -n $(NAMESPACE) --kube-context $(KUBE_CONTEXT)
	kubectl --context $(KUBE_CONTEXT) -n $(NAMESPACE) get pods,svc,ingress,pvc

undeploy-local: ## helm uninstall (Secret erms-secrets and the MySQL PVC are kept)
	helm uninstall $(RELEASE) -n $(NAMESPACE) --kube-context $(KUBE_CONTEXT) --wait

##@ Agents
.PHONY: agent-loop
agent-loop: ## Autonomous implement (claude-opus-5.5) ↔ test (non-Claude) loop: make agent-loop TASK="…" [ARGS="--max-iterations 3"]
	@test -n "$(TASK)" || { echo 'usage: make agent-loop TASK="what to build" [ARGS="…"]'; exit 2; }
	scripts/agent-loop.sh --task "$(TASK)" $(ARGS)

##@ Self-hosted runner
.PHONY: runner-setup runner-uninstall
runner-setup: ## Install/register the self-hosted runner + repo hardening (idempotent)
	scripts/setup-local-runner.sh

runner-uninstall: ## Unregister and remove the self-hosted runner service
	scripts/setup-local-runner.sh --uninstall
