{{/*
Chart name.
*/}}
{{- define "erms.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Full name prefix. With release "erms" and chart "erms" this is "erms", so resources are
erms-backend, erms-frontend, erms-mysql, erms-secrets.
*/}}
{{- define "erms.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "erms.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Component resource names */}}
{{- define "erms.backend.fullname" -}}{{ include "erms.fullname" . }}-backend{{- end }}
{{- define "erms.frontend.fullname" -}}{{ include "erms.fullname" . }}-frontend{{- end }}
{{- define "erms.mysql.fullname" -}}{{ include "erms.fullname" . }}-mysql{{- end }}

{{/*
Name of the Secret holding mysql-root-password, mysql-password and jwt-secret.
*/}}
{{- define "erms.secretName" -}}
{{- if .Values.secrets.existingSecret }}
{{- .Values.secrets.existingSecret }}
{{- else }}
{{- include "erms.fullname" . }}-secrets
{{- end }}
{{- end }}

{{- define "erms.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "erms.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Common labels. Call with (dict "ctx" $ "component" "backend" "version" "<tag>").
*/}}
{{- define "erms.labels" -}}
helm.sh/chart: {{ include "erms.chart" .ctx }}
{{ include "erms.selectorLabels" . }}
app.kubernetes.io/version: {{ (default .ctx.Chart.AppVersion .version) | toString | replace "+" "_" | trunc 63 | trimSuffix "-" | quote }}
app.kubernetes.io/managed-by: {{ .ctx.Release.Service }}
app.kubernetes.io/part-of: erms
{{- end }}

{{/*
Selector labels. Call with (dict "ctx" $ "component" "backend").
*/}}
{{- define "erms.selectorLabels" -}}
app.kubernetes.io/name: {{ include "erms.name" .ctx }}
app.kubernetes.io/instance: {{ .ctx.Release.Name }}
{{- if .component }}
app.kubernetes.io/component: {{ .component }}
{{- end }}
{{- end }}

{{/*
Application image. Call with (dict "ctx" $ "image" .Values.backend.image).
*/}}
{{- define "erms.appImage" -}}
{{- $tag := default .ctx.Chart.AppVersion .image.tag | toString }}
{{- if .ctx.Values.imageRegistry }}
{{- printf "%s/%s:%s" (trimSuffix "/" .ctx.Values.imageRegistry) .image.repository $tag }}
{{- else }}
{{- printf "%s:%s" .image.repository $tag }}
{{- end }}
{{- end }}

{{/*
Third-party image (mysql, busybox, curl): repository:tag as given.
*/}}
{{- define "erms.image" -}}
{{- printf "%s:%s" .repository (.tag | toString) }}
{{- end }}

{{/*
Restricted container securityContext (Pod Security Standards "restricted").
*/}}
{{- define "erms.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
privileged: false
capabilities:
  drop:
    - ALL
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{/*
JDBC URL for the backend.
*/}}
{{- define "erms.datasourceUrl" -}}
{{- if .Values.backend.datasource.url }}
{{- .Values.backend.datasource.url }}
{{- else }}
{{- printf "jdbc:mysql://%s:3306/%s?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" (include "erms.mysql.fullname" .) .Values.mysql.database }}
{{- end }}
{{- end }}
