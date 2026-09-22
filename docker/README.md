# GenChat-X Local Infrastructure

Starts MySQL, Redis, MinIO, and PgVector only. Run the backend and frontend on the host machine.

The Compose project and network are named `genchat-x`; containers use the `genchat-x-` prefix. Named volumes are new, isolated volumes (for example `genchat-x_mysql-data`), not the original GenChat volumes. Existing data is not migrated automatically.

Run commands below from the GenChat-X project root. If GenChat is running at the same time, choose different host ports in `.env` and update the backend connection settings accordingly. Do not remove the original project's volumes.

## Start

```bash
# 1. Optional: copy the environment template
cp .env.example .env

# 2. Start infrastructure
docker compose up -d

# 3. Check status
docker compose ps
```

## Stop

```bash
docker compose down
```

Remove volumes as well (deletes local database and object storage data):

```bash
docker compose down -v
```

## Services & Ports

| Service | Port | Default credentials | Purpose |
|---------|------|---------------------|---------|
| MySQL | 3306 | `root` / `your_password`, database `chat_agent` | Sessions, files, PPT metadata (Flyway migrations) |
| Redis | 6379 | No password | Agent task state and stop signals |
| MinIO API | 19000 | `ak` / `sk`, bucket `test` | File and generated asset object storage |
| MinIO Console | 19001 | Same as above | Web admin UI |
| PgVector | 5433 | `vectoruser` / `123456`, database `vectordb` | File RAG vector storage |

Current Compose images are `mysql:8.0`, `redis:7-alpine`, `minio/minio:latest`, `minio/mc:latest`, and `pgvector/pgvector:pg16`. The MySQL and MinIO tags are floating; pin tested image digests or exact versions for reproducible production deployments.

## Health, Logs, and Backups

```bash
# Health and recent logs
docker compose ps
docker compose logs --tail=200 mysql redis minio pgvector

# Logical database backups (adjust credentials if .env overrides defaults)
docker compose exec -T mysql mysqldump -uroot -pyour_password --databases chat_agent > mysql-backup.sql
docker compose exec -T pgvector pg_dump -U vectoruser vectordb > pgvector-backup.sql
```

Back up the named volumes or export MinIO objects separately before `docker compose down -v`. Never commit backup files containing user data or credentials.

## Run Backend & Frontend

```bash
# Backend (Java 25; requires the sibling ../agent-foundation project)
./gradlew bootRun

# Frontend
cd frontend && npm ci && npm run dev
```

See `.env.example` in the project root for LLM and other external API keys.

Compose reads `.env`, but a host-side `bootRun` does not automatically load it. Export the backend environment variables or configure `src/main/resources/application-local.yml` using its `.example` template. For parallel application instances, also change the backend port, frontend port, and Vite proxy target. See [project independence](../docs/project-independence.md) for migration boundaries.

## Notes

- `minio-init` is a one-off job that retries until MinIO accepts a client connection, creates the default bucket, and exits. Its Compose dependency is `service_started`, not a health-completed gate.
- Schema is applied by Flyway when the app starts—no manual SQL import needed.
- PgVector initialization and application schema behavior depend on the current Spring AI configuration; verify the expected vector table after the first application startup rather than assuming a table exists after Compose alone.
