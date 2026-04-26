# Nexilo

**Nexilo** est une plateforme SaaS alimentée par l'IA qui transforme des documents PDF en résumés structurés, données extractibles, réponses contextuelles (RAG) et insights actionnables.

---

## Stack Technique

| Couche | Technologie |
|---|---|
| Backend | Java 21 + Spring Boot 3.4.3 |
| IA | Spring AI 1.0.0 + Anthropic Claude (`claude-sonnet-4-5`) |
| Embeddings | OpenAI `text-embedding-3-small` |
| Base de données | PostgreSQL 16 + pgvector |
| Cache | Redis 7 |
| Stockage | Disque local (dev) / MinIO S3 (prod) |
| Migrations | Flyway |
| Sécurité | Spring Security + JWT (jjwt 0.12.5) |
| Documentation | SpringDoc OpenAPI / Swagger UI |

---

## Démarrage rapide

### Prérequis

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (ou Docker + Docker Compose)
- Une clé API Anthropic → [console.anthropic.com](https://console.anthropic.com/api-keys)
- Une clé API OpenAI → [platform.openai.com](https://platform.openai.com/api-keys) *(pour les embeddings RAG)*

### 1. Cloner le dépôt

```bash
git clone <votre-url>
cd nexilo
```

### 2. Configurer les variables d'environnement

```bash
cp .env.example .env
```

Éditez `.env` et renseignez **au minimum** :

```env
ANTHROPIC_API_KEY=sk-ant-api03-...   # requis
OPENAI_API_KEY=sk-proj-...           # requis pour Q&A / RAG
```

> ⚠️ **Ne commitez jamais le fichier `.env`** — il est dans `.gitignore`.

### 3. Lancer l'environnement complet

```bash
docker-compose up -d
```

Docker démarre automatiquement :
- **PostgreSQL 16** + pgvector (port 5432)
- **Redis 7** (port 6379)
- **MinIO** S3-compatible (ports 9000 + 9001)
- **Nexilo App** Spring Boot (port 8080)

Attendre ~60s le premier démarrage (téléchargement des images + build Maven).

```bash
# Suivre les logs de l'application
docker-compose logs -f nexilo-app
```

### 4. Accéder aux services

| Service | URL | Identifiants |
|---|---|---|
| **API REST** | http://localhost:8080/api/v1 | JWT requis |
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html | — |
| **Health check** | http://localhost:8080/api/v1/health | public |
| **MinIO Console** | http://localhost:9001 | nexilo / nexilo123 |

---

## Développement sans Docker

### Prérequis locaux

- JDK 21
- Maven 3.9+
- PostgreSQL 16 + extension pgvector
- Redis 7

### Lancer uniquement l'infrastructure

```bash
# Démarrer PostgreSQL + Redis + MinIO sans l'app
docker-compose up -d postgres redis minio
```

### Lancer l'application en local

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Authentification

### S'inscrire

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "motdepasse123"
}
```

### Se connecter

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "motdepasse123"
}
```

### Utiliser le token dans Swagger

1. Appelez `/login` → copiez le `token`
2. Cliquez **Authorize 🔒** dans Swagger UI
3. Collez le token (sans le préfixe `Bearer `)

---

## Fonctionnalités principales

### Résumé PDF

```http
POST /api/v1/documents/summarize
Authorization: Bearer <token>
Content-Type: multipart/form-data

file: <votre-pdf>
```

Retourne un résumé structuré (résumé exécutif, points clés, thèmes, langue).

### Q&A sur un document (RAG)

```http
POST /api/v1/qna/{documentId}/ask
Authorization: Bearer <token>
Content-Type: application/json

{ "question": "Quelle est la conclusion principale ?" }
```

### Extraction de données structurées

```http
POST /api/v1/documents/{documentId}/extract
Authorization: Bearer <token>
Content-Type: application/json

{ "templateId": "INVOICE" }
```

Templates disponibles : `INVOICE`, `CONTRACT`, `CV_RESUME`, `MEDICAL`

### Jobs asynchrones

```http
# Soumettre un job
POST /api/v1/jobs
{ "type": "INGEST", "documentId": "uuid" }

# Polling du statut
GET /api/v1/jobs/{jobId}
```

### Quotas et usage

```http
GET /api/v1/quota/me          # Quotas restants
GET /api/v1/usage/me          # Usage du jour
GET /api/v1/usage/me/history?period=MONTH
```

---

## Plans et quotas

| Feature | FREE | PRO | ENTERPRISE |
|---|---|---|---|
| Résumés / jour | 5 | 100 | ∞ |
| Q&A / jour | 10 | 500 | ∞ |
| Extractions / jour | 2 | 50 | ∞ |
| Fichier max | 10 Mo | 100 Mo | 500 Mo |
| Workflows | ✗ | ✓ | ✓ |

---

## Structure du projet

```
src/main/java/com/nexilo/
├── ai/
│   ├── extraction/   → Extraction de données structurées
│   ├── provider/     → Providers IA (Claude, Gemini)
│   ├── qna/          → Q&A RAG + ingestion vectorielle
│   └── summary/      → Résumé PDF
├── common/
│   ├── config/       → Redis, Async, Spring AI, OpenAPI
│   ├── exception/    → GlobalExceptionHandler, ErrorCodes
│   └── security/     → JWT, Spring Security
├── infra/
│   └── queue/        → Jobs asynchrones (AiJob, AiJobProcessor)
├── storage/          → FileStorageService (Local / MinIO)
├── usage/            → Tracking tokens + coûts
├── user/
│   ├── entity/       → User, UserPlan, PlanConfig
│   └── quota/        → QuotaService, @CheckQuota, Aspect
└── processing/       → Pipeline de traitement PDF
```

---

## Migrations Flyway

| Version | Description |
|---|---|
| V1 | Schéma initial (users, documents) |
| V2 | pgvector + vector_store |
| V3 | Tables résumés (summary_documents, summaries) |
| V4 | document_chunks (RAG) |
| V5 | Champs ingestion (ingested, ingested_at) |
| V6 | Tables Q&A (sessions, messages) |
| V7 | Tables extraction (templates, résultats) |
| V8 | Jobs IA asynchrones (ai_jobs) |
| V9 | Plan utilisateur (plan, plan_expires_at) |
| V10 | Tracking usage (usage_records) |
| V11 | Colonnes stockage (storage_key, storage_url) |

---

## Gestion Docker

```bash
# Démarrer
docker-compose up -d

# Arrêter (sans supprimer les données)
docker-compose down

# Réinitialiser complètement (supprime toutes les données)
docker-compose down -v && docker-compose up -d

# Rebuilder l'image de l'app après modification du code
docker-compose build nexilo-app && docker-compose up -d nexilo-app

# Logs en temps réel
docker-compose logs -f nexilo-app
docker-compose logs -f postgres
```

---

## Variables d'environnement

| Variable | Description | Défaut |
|---|---|---|
| `ANTHROPIC_API_KEY` | Clé API Claude (**requis**) | — |
| `OPENAI_API_KEY` | Clé API OpenAI pour embeddings | `dummy` |
| `GOOGLE_AI_API_KEY` | Clé API Gemini (optionnel) | `dummy` |
| `DB_PASSWORD` | Mot de passe PostgreSQL | `postgres` |
| `REDIS_PASSWORD` | Mot de passe Redis (vide = aucun) | — |
| `MINIO_ROOT_USER` | Utilisateur MinIO | `nexilo` |
| `MINIO_ROOT_PASSWORD` | Mot de passe MinIO | `nexilo123` |
| `JWT_SECRET` | Clé de signature JWT (hex 256 bits) | valeur dev |
| `STORAGE_PATH` | Répertoire uploads (profil dev) | `./uploads` |
