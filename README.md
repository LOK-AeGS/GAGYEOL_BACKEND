# GAGYEOL (가결) - 지출결의서 AI 자동화 시스템

대학 동아리 및 조직의 지출결의서를 AI로 자동화하고 다단계 결재를 지원하는 Spring Boot 백엔드입니다.

## 주요 기능

- **증빙서류 AI 분석**: 영수증/거래명세서 업로드 → Upstage IE로 결제수단 분류 및 필드 자동 추출
- **양식지 자동 채우기**: 추출된 필드를 DOCX/XLSX 양식지에 자동 매핑 후 다운로드
- **규정 검토 (RAG)**: 규정책 PDF를 벡터 DB에 인덱싱하여 GPT 분석 시 관련 규정 자동 참조
- **다단계 결재 워크플로우**: 역할 기반 순차 승인, 동시성 제어(비관적 락), 재결재 지원
- **그룹 관리**: 역할 계층, 초대코드 기반 가입, 멤버 관리

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| 프레임워크 | Spring Boot 3.4.4, Java 17 |
| 데이터베이스 | PostgreSQL 16 + pgvector |
| DB 마이그레이션 | Flyway |
| 인증 | JWT (HS256, 24시간 유효) |
| 문서 처리 | Apache POI (DOCX/XLSX), Apache PDFBox |
| AI | OpenAI GPT-4o, text-embedding-3-small |
| 외부 API | Upstage Information Extract API |
| 빌드 | Gradle 8.x |
| 배포 | Docker, Docker Compose |

---

## 프로젝트 구조

```
src/main/java/GAGYELOL/
├── config/                    # Security, JWT, 전역 예외 처리
├── controller/                # REST 컨트롤러 (7개, 40+ 엔드포인트)
├── service/                   # 비즈니스 로직 (15개 서비스)
│   ├── AI 서비스              # FormAiService, EvidenceAiService, PolicyAiService
│   ├── 문서 처리              # FormParserService, FormFillService, PdfParserService
│   └── 외부 클라이언트       # OpenAiClient, UpstageIeClient, EmbeddingService
├── entity/                    # JPA 엔티티 (11개)
├── repository/                # Spring Data JPA Repository (11개 + pgvector)
└── dto/                       # 요청/응답 DTO (20+)

src/main/resources/
├── application.properties
└── db/migration/              # Flyway 마이그레이션 스크립트
```

---

## 도메인 및 API 엔드포인트

### 인증 `/api/auth`
| 메서드 | URL | 설명 |
|--------|-----|------|
| POST | `/register` | 회원가입 |
| POST | `/login` | 로그인 (JWT 발급) |

### 그룹 관리 `/api/groups`
| 메서드 | URL | 설명 | 권한 |
|--------|-----|------|------|
| POST | `/` | 그룹 생성 (역할 목록 포함) | 생성자 |
| POST | `/join/{inviteCode}` | 초대코드로 그룹 가입 | 가입자 |
| GET | `/{groupId}` | 그룹 상세 조회 | 멤버 |
| GET | `/my` | 내 그룹 목록 | 본인 |
| DELETE | `/{groupId}/members/{userId}` | 멤버 추방 | 대표자 |
| PUT | `/{groupId}/members/role` | 역할 변경 | 대표자 |
| POST | `/{groupId}/roles` | 역할 추가 | 대표자 |
| PUT | `/{groupId}/roles/{roleId}` | 역할명 수정 | 대표자 |
| DELETE | `/{groupId}/roles/{roleId}` | 역할 삭제 | 대표자 |
| PUT | `/{groupId}/payer-info` | 지출인 정보 등록/수정 | 대표자 |

### 양식지 `/api/forms`
| 메서드 | URL | 설명 |
|--------|-----|------|
| POST | `/upload` | DOCX 양식지 업로드 및 AI 분석 |
| POST | `/{formId}/reanalyze` | 특정 정책 기반 재분석 |
| GET | `/` | 그룹별 양식지 목록 (`?groupId=`) |
| GET | `/{id}` | 양식지 단건 조회 |
| DELETE | `/{id}` | 양식지 삭제 |

### 증빙서류 `/api/evidence`
| 메서드 | URL | 설명 |
|--------|-----|------|
| GET | `/` | 그룹별 증빙서류 목록 (`?groupId=`) |
| GET | `/{evidenceId}` | 증빙서류 단건 조회 |
| DELETE | `/{evidenceId}` | 증빙서류 삭제 |
| POST | `/analyze` | 증빙서류 분석 + 양식지 선택 |
| POST | `/{evidenceId}/fill` | 필드 값 채우기 (수동 수정) |
| POST | `/{evidenceId}/complete` | 완성된 양식지 다운로드 (DOCX/ZIP) |

### 결재 `/api/approvals`
| 메서드 | URL | 설명 |
|--------|-----|------|
| POST | `/` | 결재 요청 생성 |
| GET | `/{requestId}` | 결재 요청 단건 조회 |
| GET | `/{requestId}/history` | 결재 이력 조회 (편집 이력 포함) |
| GET | `/my` | 내 결재 요청 목록 |
| GET | `/group/{groupId}` | 그룹 결재 목록 |
| POST | `/{requestId}/resubmit` | 재결재 (반려/취소 건) |
| POST | `/{requestId}/approve` | 승인 (의견 포함) |
| POST | `/{requestId}/reject` | 반려 (의견 포함) |
| PUT | `/{requestId}/fields` | 양식지 필드 수정 |

### 규정책 `/api/policies`
| 메서드 | URL | 설명 |
|--------|-----|------|
| POST | `/upload` | PDF 규정책 업로드 + RAG 인덱싱 |
| GET | `/` | 그룹별 규정책 목록 (`?groupId=`) |
| GET | `/{id}` | 규정책 단건 조회 |
| DELETE | `/{id}` | 규정책 삭제 (벡터 청크 포함) |

### 대시보드 `/api/dashboard`
| 메서드 | URL | 설명 |
|--------|-----|------|
| GET | `/summary` | 이번 달 제출 건수, 금액, 규정 준수율 등 |
| GET | `/monthly-directory` | 연/월별 결의서 목록 |
| GET | `/recent-approvals` | 최근 결재 현황 (상태 필터 가능) |

---

## 데이터 모델

```
User ──< GroupMember >── UserGroup
                              │
                    GroupRole (approval_order)
                              │
                          Policy (activePolicy)

Evidence ── EvidenceForm ── Form

ApprovalRequest ──< ApprovalStep (결재자별 스텝)
                └── ApprovalEditHistory (필드 수정 이력)
```

### 결재 상태
- `DRAFT` → `IN_PROGRESS` → `APPROVED` / `REJECTED` / `CANCELED`

### 결재 스텝 상태
- `PENDING` / `APPROVED` / `REJECTED` / `CANCELED`

---

## 주요 워크플로우

### 1. 증빙서류 분석 → 양식지 채우기
```
증빙 파일 업로드
→ Upstage IE: 결제수단 분류 (CARD/CASH)
→ Upstage IE: 스키마 기반 필드 추출
→ PolicyChunkVectorStore: 관련 규정 RAG 검색
→ GPT-4o: 적절한 양식지 자동 선택
→ 사용자 필드 수정 (선택)
→ FormFillService: DOCX/XLSX에 값 채우기
→ 완성 파일 다운로드
```

### 2. 다단계 결재 프로세스
```
결재 요청 생성
→ 요청자보다 높은 모든 역할에 PENDING 스텝 생성
→ 각 단계 순차 승인 (currentApprovalOrder 기반)
→ 한 단계 전원 승인 시 자동 다음 단계 진행
→ 반려 시 현재 단계 모든 PENDING 스텝 자동 반려
→ 재결재: REJECTED/CANCELED 건에 한해 가능
```

### 3. RAG 기반 정책 검토
```
PDF 규정책 업로드
→ PDFBox 텍스트 추출 (이미지 기반 시 GPT-4o Vision OCR)
→ 청킹 → text-embedding-3-small 임베딩
→ pgvector 저장 (1536 차원)
→ 양식지/증빙서류 분석 시 코사인 유사도 TOP_K=5 검색
→ GPT 프롬프트에 컨텍스트로 제공
```

---

## 로컬 환경 설정

### 1. 필수 환경변수

`.env.example`을 복사하여 `.env` 파일 생성:

```bash
cp .env.example .env
```

`.env` 파일에 API 키 설정:
```
OPENAI_API_KEY=<OpenAI API 키>
UPSTAGE_API_KEY=<Upstage API 키>
```

### 2. Docker Compose로 실행 (권장)

```bash
docker-compose up --build
```

- PostgreSQL (pgvector) + Spring Boot 앱이 함께 실행됩니다.
- DB: `localhost:5432/gagyelol`
- API: `http://localhost:8080`

### 3. 로컬 개발 실행

PostgreSQL (pgvector 확장 포함) 실행 후:

```bash
# DB만 Docker로 실행
docker-compose up db

# 앱은 IDE 또는 Gradle로 실행
./gradlew bootRun
```

---

## 보안

- **JWT 인증**: 모든 API에 `Authorization: Bearer <token>` 헤더 필요 (공개 엔드포인트 제외)
- **비밀번호**: BCrypt 암호화
- **CORS**: localhost:3000, localhost:5173, Vercel 배포 주소 허용
- **동시성 제어**: 결재 승인 시 비관적 락 (PESSIMISTIC_WRITE) 적용
- **감사 보존**: 결재 요청에서 참조 중인 증빙서류 삭제 차단

---

## 외부 서비스 연동

| 서비스 | 용도 | 모델/설정 |
|--------|------|-----------|
| OpenAI GPT-4o | 양식지 분석, 양식지 선택 | JSON 모드, temperature=0.1 |
| OpenAI GPT-4o Vision | 이미지 기반 PDF OCR | 일반 모드 |
| text-embedding-3-small | 규정책 청킹 임베딩 | 1536 차원 |
| Upstage IE | 증빙서류 결제수단 분류 + 필드 추출 | JSON 스키마 기반 |

---

## 참고사항

- Flyway로 DB 스키마가 자동 관리됩니다 (`src/main/resources/db/migration/`)
- 파일 업로드 경로: `./uploads/policies`, `./uploads/forms`, `./uploads/evidence`, `./uploads/photos`
- Docker 실행 시 `uploads` 폴더는 볼륨으로 마운트됩니다

---

## API 명세

### 공통

- **Base URL**: `http://localhost:8080`
- **인증**: 로그인/회원가입을 제외한 모든 요청에 헤더 필요
  ```
  Authorization: Bearer <JWT 토큰>
  ```
- **Content-Type**: JSON 요청은 `application/json`, 파일 업로드는 `multipart/form-data`
- **에러 응답 형식**: `{ "error": "에러 메시지" }`

---

### 인증 `/api/auth`

#### POST `/api/auth/register` — 회원가입
> 인증 불필요

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```
- `email`: 이메일 형식, 필수
- `password`: 8~20자, 필수
- `name`: 필수

**Response** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동"
}
```

---

#### POST `/api/auth/login` — 로그인
> 인증 불필요

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동"
}
```

---

### 그룹 `/api/groups`

#### POST `/api/groups` — 그룹 생성

**Request Body**
```json
{
  "name": "단국대학교 컴퓨터공학과",
  "roles": ["부원", "차장", "부장"]
}
```
- `roles`: 결재 순서 오름차순 (첫 번째가 최하위, 마지막이 최고위). 생성자는 최고위 역할로 자동 배정

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### POST `/api/groups/join/{inviteCode}` — 초대코드로 가입

**Path Param**: `inviteCode` — 그룹 초대코드

**Response** `200 OK` → [GroupResponse](#groupresponse)
> 가입 시 최하위 역할로 자동 배정

---

#### GET `/api/groups/{groupId}` — 그룹 상세 조회

**Path Param**: `groupId`

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### GET `/api/groups/my` — 내 그룹 목록

**Response** `200 OK` → `Array<GroupResponse>`

---

#### DELETE `/api/groups/{groupId}/members/{userId}` — 멤버 추방
> 그룹 대표자만 가능. 진행 중인 결재의 PENDING 스텝 자동 취소

**Path Params**: `groupId`, `userId` (추방 대상)

**Response** `204 No Content`

---

#### PUT `/api/groups/{groupId}/members/role` — 역할 변경
> 그룹 대표자만 가능

**Path Param**: `groupId`

**Request Body**
```json
{
  "userId": 2,
  "roleId": 3
}
```

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### POST `/api/groups/{groupId}/roles` — 역할 추가
> 그룹 대표자만 가능

**Request Body**
```json
{
  "roleName": "총무",
  "approvalOrder": 2
}
```

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### PUT `/api/groups/{groupId}/roles/{roleId}` — 역할명 수정

**Request Body**
```json
{
  "roleName": "팀장",
  "approvalOrder": 3
}
```

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### DELETE `/api/groups/{groupId}/roles/{roleId}` — 역할 삭제
> 해당 역할에 속한 멤버가 없을 때만 삭제 가능

**Response** `204 No Content`

---

#### PUT `/api/groups/{groupId}/payer-info` — 지출인 정보 등록/수정
> 양식지의 "지출인" 필드 자동 채우기에 사용

**Request Body**
```json
{
  "name": "홍길동",
  "affiliation": "컴퓨터공학과",
  "studentId": "32210000",
  "phone": "010-1234-5678"
}
```

**Response** `200 OK` → [GroupResponse](#groupresponse)

---

#### GroupResponse

```json
{
  "groupId": 1,
  "name": "단국대학교 컴퓨터공학과",
  "inviteCode": "ABC12345",
  "ownerName": "홍길동",
  "roles": [
    { "roleId": 1, "roleName": "부원", "approvalOrder": 0 },
    { "roleId": 2, "roleName": "차장", "approvalOrder": 1 },
    { "roleId": 3, "roleName": "부장", "approvalOrder": 2 }
  ],
  "members": [
    {
      "userId": 1,
      "name": "홍길동",
      "email": "user@example.com",
      "roleName": "부장",
      "approvalOrder": 2
    }
  ],
  "payerInfo": {
    "name": "홍길동",
    "affiliation": "컴퓨터공학과",
    "studentId": "32210000",
    "phone": "010-1234-5678"
  }
}
```

---

### 사진 `/api/photos`

#### POST `/api/photos/upload` — 사진 업로드 (학생증, 도장 등)
> 반환된 `filePath`를 양식지 완성 요청의 `imageFields`에 사용

**Content-Type**: `multipart/form-data`

**Form Params**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `file` | File | ✅ | 이미지 파일 (jpg, jpeg, png, webp, gif) |
| `label` | String | ❌ | 사진 용도 라벨 (기본값: "사진") |

**Response** `200 OK`
```json
{
  "photoId": "a1b2c3d4-e5f6-...",
  "filePath": "./uploads/photos/a1b2c3d4-....jpg",
  "fileName": "student_id.jpg",
  "label": "학생증",
  "uploadedAt": "2026-05-09T18:30:00"
}
```
> `filePath` 값을 `/api/evidence/{id}/complete`의 `imageFields`에 직접 사용

---

#### DELETE `/api/photos/{photoId}` — 사진 삭제

**Path Param**: `photoId` — 업로드 응답의 `photoId`

**Response** `204 No Content`

---

### 규정책 `/api/policies`

#### POST `/api/policies/upload` — 규정책 PDF 업로드
> PDF 텍스트 추출 → 청킹 → text-embedding-3-small 임베딩 → pgvector 저장 (RAG 인덱싱)

**Content-Type**: `multipart/form-data`

**Form Params**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `file` | File | ✅ | PDF 파일 |
| `policyName` | String | ✅ | 규정책 이름 |
| `groupId` | Long | ❌ | 그룹 ID |

**Response** `200 OK`
```json
{
  "policyId": 1,
  "policyName": "2026년 동아리 운영 규정",
  "chunkCount": 42,
  "message": "규정책 업로드 및 벡터 인덱싱 완료"
}
```

---

#### GET `/api/policies?groupId={groupId}` — 그룹별 규정책 목록

**Response** `200 OK`
```json
[
  {
    "policyId": 1,
    "policyName": "2026년 동아리 운영 규정",
    "filePath": "./uploads/policies/xxx.pdf",
    "createdAt": "2026-05-01T10:00:00"
  }
]
```

---

#### GET `/api/policies/{id}` — 규정책 단건 조회

**Response** `200 OK` → 단건 PolicyResponse (위 목록 항목 형식과 동일)

---

#### DELETE `/api/policies/{id}` — 규정책 삭제
> pgvector 청크도 함께 삭제

**Response** `204 No Content`

---

### 양식지 `/api/forms`

#### POST `/api/forms/upload` — 양식지 업로드 및 AI 분석
> DOCX/XLSX/XLS 업로드 → 텍스트 추출 → (규정 RAG 검색) → GPT-4o 필드 분석

**Content-Type**: `multipart/form-data`

**Form Params**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `file` | File | ✅ | 양식지 파일 (docx, xlsx, xls) |
| `formName` | String | ✅ | 양식지 이름 |
| `paymentType` | String | ❌ | 결제수단 (`CARD` / `CASH` / `BOTH`, 기본값: `BOTH`) |
| `policyId` | Long | ❌ | 분석 시 참조할 규정책 ID |
| `groupId` | Long | ❌ | 그룹 ID |

**Response** `200 OK`
```json
{
  "formId": 1,
  "formName": "현금지출증빙서",
  "description": "현금으로 지출한 경우 사용. 5만원 이하 소액 지출에 적합.",
  "paymentType": "CASH",
  "fields": ["날짜", "사업명", "지출인 이름", "지출인 소속", "금액", "합계"],
  "generatedFields": ["사업 목적"],
  "createdAt": "2026-05-09T10:00:00"
}
```
- `fields`: 증빙서류에서 자동 추출 가능한 필드 목록
- `generatedFields`: 사업명 기반으로 LLM이 생성하는 필드 목록

---

#### POST `/api/forms/{formId}/reanalyze` — 양식지 재분석

**Query Param**: `policyId` (선택)

**Response** `200 OK` → 위 FormUploadResponse 형식

---

#### GET `/api/forms?groupId={groupId}` — 그룹별 양식지 목록

**Response** `200 OK` → `Array<FormUploadResponse>`

---

#### GET `/api/forms/{id}` — 양식지 단건 조회

**Response** `200 OK` → FormUploadResponse

---

#### DELETE `/api/forms/{id}` — 양식지 삭제

**Response** `204 No Content`

---

### 증빙서류 `/api/evidence`

#### POST `/api/evidence/analyze` — 증빙서류 분석
> 파일 업로드 → Upstage IE: 결제수단 분류 → 사용 가능한 양식지 목록 반환

**Content-Type**: `multipart/form-data`

**Form Params**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `file` | File | ✅ | 증빙서류 (pdf, jpg, jpeg, png, webp, docx, xlsx, xls, hwp, hwpx) |
| `groupId` | Long | ❌ | 그룹 ID (그룹의 활성 규정책 자동 연결) |
| `businessName` | String | ❌ | 사업명 (LLM 생성 필드에 사용) |
| `recipientImage` | File | ❌ | 수령인 서명/도장 사진 |

**Response** `200 OK`
```json
{
  "evidenceId": 10,
  "paymentType": "CASH",
  "extractedText": "",
  "availableForms": [
    {
      "formId": 1,
      "formName": "현금지출증빙서",
      "description": "현금 소액 지출에 사용",
      "paymentType": "CASH",
      "fields": ["날짜", "사업명", "금액", "합계"]
    }
  ]
}
```

---

#### POST `/api/evidence/{evidenceId}/fill` — 필드 자동 채우기
> Upstage IE로 증빙서류에서 필드 값 추출. 지출인 정보는 그룹 등록 데이터로 사전 채우기

**Request Body**
```json
{
  "formIds": [1, 2]
}
```

**Response** `200 OK`
```json
{
  "evidenceId": 10,
  "results": [
    {
      "formId": 1,
      "formName": "현금지출증빙서",
      "filledFields": {
        "날짜": "2026-05-09",
        "금액": "15000",
        "지출인 이름": "홍길동"
      },
      "missingFields": ["합계", "사업명"]
    }
  ]
}
```

---

#### POST `/api/evidence/{evidenceId}/complete` — 완성 양식지 다운로드
> 필드 값 + 이미지를 양식지에 채워 DOCX/XLSX/ZIP 파일로 반환

**Request Body**
```json
{
  "forms": [
    {
      "formId": 1,
      "filledFields": {
        "날짜": "2026-05-09",
        "금액": "15000"
      },
      "userInputFields": {
        "합계": "15000원",
        "사업명": "MT 준비물 구매"
      },
      "imageFields": {
        "사진": "./uploads/photos/a1b2c3d4-....jpg",
        "영수증 사진": "evidence"
      }
    }
  ]
}
```

**imageFields 소스 값**
| 값 | 설명 |
|----|------|
| `"evidence"` | 업로드한 증빙서류 파일 자체 (이미지 형식인 경우) |
| `"recipientImage"` | analyze 시 업로드한 수령인 사진 |
| 파일 경로 | `/api/photos/upload` 응답의 `filePath` 값 |

**Response**
- 양식지 1개: `200 OK`, `Content-Type: application/octet-stream`, 파일명 `completed_form.docx` / `.xlsx` / `.xls`
- 양식지 여러 개: `200 OK`, `Content-Type: application/zip`, 파일명 `completed_forms.zip`

---

#### GET `/api/evidence?groupId={groupId}` — 그룹별 증빙서류 목록

**Response** `200 OK`
```json
[
  {
    "evidenceId": 10,
    "groupId": 1,
    "policyId": 1,
    "fileName": "receipt.jpg",
    "filePath": "./uploads/evidence/xxx.jpg",
    "extractedText": "",
    "selectedFormIds": [1],
    "createdAt": "2026-05-09T10:00:00"
  }
]
```

---

#### GET `/api/evidence/{evidenceId}` — 증빙서류 단건 조회

**Response** `200 OK` → 위 목록 항목 형식

---

#### DELETE `/api/evidence/{evidenceId}` — 증빙서류 삭제
> 진행 중인 결재 요청에서 참조 중이면 삭제 거부

**Response** `204 No Content`

---

### 결재 `/api/approvals`

#### POST `/api/approvals` — 결재 요청 생성
> 요청자보다 높은 모든 역할에 순차적으로 결재 요청 생성

**Request Body**
```json
{
  "groupId": 1,
  "evidenceId": 10,
  "formId": 1,
  "filledFields": {
    "날짜": "2026-05-09",
    "금액": "15000",
    "합계": "15000원"
  },
  "parentRequestId": null
}
```
- `parentRequestId`: 재결재 시 원본 결재 ID (선택)

**Response** `200 OK` → [ApprovalResponse](#approvalresponse)

---

#### GET `/api/approvals/{requestId}` — 결재 단건 조회

**Response** `200 OK` → [ApprovalResponse](#approvalresponse)

---

#### GET `/api/approvals/{requestId}/history` — 결재 이력 조회

**Response** `200 OK`
```json
{
  "requestId": 5,
  "status": "IN_PROGRESS",
  "currentApprovalOrder": 2,
  "filledFields": { "날짜": "2026-05-09", "금액": "15000" },
  "steps": [
    {
      "approverName": "김차장",
      "approvalOrder": 1,
      "roleName": "차장",
      "action": "APPROVED",
      "comment": "확인했습니다.",
      "actedAt": "2026-05-09T11:00:00"
    }
  ],
  "editHistory": [
    {
      "editId": 1,
      "editorName": "홍길동",
      "beforeFields": { "금액": "10000" },
      "afterFields": { "금액": "15000" },
      "editedAt": "2026-05-09T10:30:00"
    }
  ]
}
```

---

#### GET `/api/approvals/my` — 내 결재 요청 목록

**Response** `200 OK` → `Array<ApprovalResponse>`

---

#### GET `/api/approvals/group/{groupId}` — 그룹 결재 목록

**Response** `200 OK` → `Array<ApprovalResponse>`

---

#### POST `/api/approvals/{requestId}/approve` — 승인
> 현재 결재 단계의 결재자만 가능. 해당 단계 전원 승인 시 다음 단계 자동 진행

**Request Body**
```json
{
  "comment": "내용 확인 후 승인합니다."
}
```

**Response** `200 OK` → [ApprovalResponse](#approvalresponse)

---

#### POST `/api/approvals/{requestId}/reject` — 반려
> 현재 단계 모든 PENDING 스텝 자동 반려 처리

**Request Body**
```json
{
  "comment": "영수증 금액이 일치하지 않습니다."
}
```

**Response** `200 OK` → [ApprovalResponse](#approvalresponse)

---

#### POST `/api/approvals/{requestId}/resubmit` — 재결재
> REJECTED 또는 CANCELED 상태인 결재만 가능. 수정된 필드로 새 결재 생성

**Request Body**
```json
{
  "filledFields": {
    "날짜": "2026-05-09",
    "금액": "15000",
    "합계": "15000원"
  }
}
```

**Response** `200 OK` → [ApprovalResponse](#approvalresponse) (새로 생성된 결재)

---

#### PUT `/api/approvals/{requestId}/fields` — 양식지 필드 수정
> 요청자 또는 현재 결재 단계의 결재자만 가능. 수정 이력 자동 저장

**Request Body**
```json
{
  "filledFields": {
    "금액": "20000",
    "합계": "20000원"
  }
}
```

**Response** `200 OK` → [ApprovalResponse](#approvalresponse)

---

#### ApprovalResponse

```json
{
  "requestId": 5,
  "status": "IN_PROGRESS",
  "currentApprovalOrder": 2,
  "filledFields": {
    "날짜": "2026-05-09",
    "금액": "15000",
    "합계": "15000원"
  },
  "steps": [
    {
      "approverName": "김차장",
      "approvalOrder": 1,
      "roleName": "차장",
      "action": "APPROVED",
      "comment": "확인했습니다.",
      "actedAt": "2026-05-09T11:00:00"
    },
    {
      "approverName": "이부장",
      "approvalOrder": 2,
      "roleName": "부장",
      "action": "PENDING",
      "comment": null,
      "actedAt": null
    }
  ],
  "createdAt": "2026-05-09T10:00:00",
  "updatedAt": "2026-05-09T11:00:00"
}
```

**status 값**
| 값 | 설명 |
|----|------|
| `IN_PROGRESS` | 결재 진행 중 |
| `APPROVED` | 최종 승인 완료 |
| `REJECTED` | 반려됨 |
| `CANCELED` | 취소됨 |

**steps.action 값**
| 값 | 설명 |
|----|------|
| `PENDING` | 대기 중 |
| `APPROVED` | 승인 |
| `REJECTED` | 반려 |
| `CANCELED` | 취소 (멤버 추방/역할 변경 시 자동) |

---

### 대시보드 `/api/dashboard`

모든 대시보드 엔드포인트에 **`?groupId={groupId}`** 쿼리 파라미터 필요

#### GET `/api/dashboard/summary` — 월별 요약 통계

**Response** `200 OK`
```json
{
  "monthlySubmitCount": 12,
  "prevMonthSubmitCount": 8,
  "monthlyTotalAmount": 350000,
  "prevMonthTotalAmount": 210000,
  "inProgressCount": 3,
  "avgWaitingDays": 2.5,
  "complianceRate": 92
}
```
- `complianceRate`: 규정 준수율 (0~100)
- `avgWaitingDays`: 이번 달 평균 결재 대기 일수

---

#### GET `/api/dashboard/monthly-directory` — 연/월별 결의서 목록

**Response** `200 OK`
```json
[
  {
    "year": 2026,
    "month": 5,
    "count": 4,
    "files": [
      {
        "requestId": 5,
        "fileName": "현금지출증빙서",
        "date": "2026-05-09",
        "status": "APPROVED"
      }
    ]
  }
]
```

---

#### GET `/api/dashboard/recent-approvals` — 최근 결재 현황

**Query Params**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `groupId` | Long | 필수 |
| `status` | String | 선택: `IN_PROGRESS` / `APPROVED` / `REJECTED` |

**Response** `200 OK`
```json
{
  "reviewingCount": 3,
  "approvedCount": 7,
  "rejectedCount": 1,
  "items": [
    {
      "requestId": 5,
      "title": "현금지출증빙서",
      "expCode": "EXP-2026-005",
      "date": "2026-05-09",
      "requesterName": "홍길동",
      "status": "IN_PROGRESS"
    }
  ]
}
```

---

### 전체 워크플로우 예시

```
1. 로그인
   POST /api/auth/login

2. 그룹 생성 또는 가입
   POST /api/groups  (또는 /api/groups/join/{inviteCode})

3. 규정책 업로드 (최초 1회)
   POST /api/policies/upload

4. 양식지 업로드 (최초 1회)
   POST /api/forms/upload

5. 학생증 등 사진 업로드 (필요 시)
   POST /api/photos/upload  → filePath 획득

6. 증빙서류 분석
   POST /api/evidence/analyze  → evidenceId, availableForms 획득

7. 필드 자동 채우기
   POST /api/evidence/{evidenceId}/fill  → filledFields, missingFields 획득

8. 완성 양식지 다운로드
   POST /api/evidence/{evidenceId}/complete  (imageFields에 사진 경로 포함)

9. 결재 요청
   POST /api/approvals  (filledFields 포함)

10. 결재자 승인/반려
    POST /api/approvals/{requestId}/approve
    POST /api/approvals/{requestId}/reject
```
