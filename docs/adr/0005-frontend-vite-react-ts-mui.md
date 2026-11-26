# ADR-0005: Frontend on Vite + React 19 + TypeScript + MUI 9
Status: Accepted · 2026-11-25

## Context
CRA (react-scripts 1.1.4) is deprecated. React 16, MUI v1 and styled-components are years out of date. The URLs are hardcoded and there are no tests.

## Decision
Rewrite on Vite 8, React 19, strict TypeScript, MUI 9, react-router 7 and axios, with one API client (JWT interceptor, ProblemDetail normalisation). Testing uses Vitest, RTL and MSW. Linting uses ESLint 9 flat config and Prettier. The API base is relative `/api`. The production image is nginx-unprivileged.

## Consequences
This is a full rewrite of ~2.3k LOC, so the behaviour has to be checked against ARCHITECTURE §4.3. TS types mirror the API contract.
