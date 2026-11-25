# ADR-0006: Own Helm chart with an in-chart MySQL StatefulSet
Status: Accepted · 2026-11-25

## Context
The target is Rancher Desktop (k3s, Traefik, local-path, moby engine). Third-party MySQL charts have changed licensing and image distribution, and they add more surface than we need.

## Decision
A single `erms` chart: backend and frontend Deployments, a MySQL 8.4 StatefulSet using the official image, a Traefik Ingress on `erms.localhost`, NetworkPolicies, restricted pod security, and a chart-managed Secret that is stable across upgrades via `lookup`. `values-local.yaml` holds the Rancher Desktop overrides.

## Consequences
We own the MySQL manifest: no HA, and backups are out of scope. In production, swap in a managed DB through `existingSecret` and a DB URL override.
