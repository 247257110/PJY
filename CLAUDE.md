# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**PJY** (项目验收工作量核验系统) — A system to verify project delivery work records and detect duplicate/overlapping work assignments. Users upload PDF/Excel/image files, the system parses them (with AI assistance), detects conflicts where the same person is assigned to multiple projects during overlapping time periods, and maintains a base library of verified records.

**Stack:** Spring Boot 3.2 + MyBatis + MySQL (backend) / Vue 3 + Element Plus + Vite (frontend)

## Commands

### Backend
```bash
mvn spring-boot:run          # Run backend (port 8080)
mvn clean package            # Build JAR
mvn clean package -DskipTests
```

### Frontend
```bash
cd frontend
npm install
npm run dev       # Dev server (port 5173, proxies /api → localhost:8080)
npm run build
```

### Database
- Create MySQL database `pjy`, then execute `src/main/resources/schema.sql`
- Default admin: `admin` / `admin123`

## Architecture

### Backend (`src/main/java/org/example/`)

**Config layer:**
- `SecurityConfig` + `JwtAuthFilter` + `JwtUtil` — JWT-based auth via Spring Security; all `/api/auth/**` routes are public
- `CorsConfig` — allows all origins for development

**Core domain flow:**
1. `BaseLibController` → `BaseLibService` — manages the verified work record library; supports batch init from uploaded files
2. `VerifyController` → `VerifyService` — upload files to temp storage (`temp_record`), detect date-overlap conflicts across persons, then confirm (moves to `work_record`) or cancel
3. `PdfParseService` — parses PDF/Excel/Word documents; extracts structured work record rows
4. `AiParseService` — sends images/pages to Doubao AI (multimodal) for OCR-based parsing when standard parsing fails

**Entities:**
- `WorkRecord` — base library record (company, name, project, start/end dates, days, content)
- `TempRecord` — same structure + `batch_id` for grouping per upload session
- `ConflictResult` — DTO for detected overlaps
- `SysUser` / `SysRole` / `SysOrg` — standard system admin entities

**MyBatis:** XML mappers in `src/main/resources/mapper/`; camel-case mapping enabled; SQL logs printed in dev

### Frontend (`frontend/src/`)

**Router** (`router/index.js`): Navigation guard checks `localStorage` token. Public route: `/login`. Protected routes:
- `/base-lib` → `BaseLib.vue` — paginated table, search, delete, batch init from files
- `/verify` → `Verify.vue` — upload → preview → conflict check → confirm/cancel
- `/sys/user|role|org` — system administration

**API client** (`api/index.js`): Axios instance with interceptors — injects `Authorization: Bearer <token>` on every request; redirects to `/login` on 401/403.

### Key Config
- `src/main/resources/application.yml`: DB credentials, file upload max 50MB, Doubao AI API key
- `frontend/vite.config.js`: `/api` proxy to `http://localhost:8080`
