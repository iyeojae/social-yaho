# Frontend API Spec

기준 코드: 현재 `social-yaho` 백엔드 구현 기준
기준 Base URL: `http://localhost:8080`
API Prefix: `/api/v1`

---

## 1. 공통 규칙

### 1-1. 인증 / 사용자 식별
이 프로젝트는 현재 **실제 로그인 토큰(JWT)** 대신, 아래 헤더로 현재 사용자를 식별합니다.

```http
X-USER-ID: {userId}
```

해당 헤더가 필요한 API:
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`
- `PATCH /api/v1/users/me/membership`
- `POST /api/v1/interactions`
- `GET /api/v1/recommendations/feed`
- `POST /api/v1/recommendations/refresh`

> 참고: Spring Security 설정상 현재 모든 요청은 `permitAll` 입니다. 즉, 인증 차단은 없지만, 위 API들은 내부 로직상 `X-USER-ID`가 필요합니다.

---

### 1-2. 공통 성공 응답 포맷

```json
{
  "success": true,
  "message": "도서 목록을 조회했습니다.",
  "data": {},
  "timestamp": "2026-07-02T04:10:54.801Z"
}
```

필드 설명:
- `success`: 성공 여부
- `message`: 사용자/프론트 표시용 메시지
- `data`: 실제 응답 데이터
- `timestamp`: 응답 시각 (UTC ISO-8601)

---

### 1-3. 공통 에러 응답 포맷

```json
{
  "success": false,
  "code": "INVALID_REQUEST",
  "message": "잘못된 요청입니다.",
  "timestamp": "2026-07-02T04:11:00.000Z",
  "errors": [
    {
      "field": "nickname",
      "reason": "닉네임은 필수입니다."
    }
  ]
}
```

필드 설명:
- `success`: 항상 `false`
- `code`: 에러 코드
- `message`: 에러 메시지
- `timestamp`: 에러 발생 시각
- `errors`: 필드 검증 에러 목록 (없으면 빈 배열)

주요 에러 코드:
- `USER_NOT_FOUND`
- `EMAIL_ALREADY_EXISTS`
- `BOOK_NOT_FOUND`
- `INVALID_INTERACTION_REQUEST`
- `INVALID_REQUEST`
- `BOOK_IMPORT_PATH_NOT_FOUND`
- `BOOK_IMPORT_GENRE_MISSING`
- `BOOK_IMPORT_FAILED`
- `BOOK_IMPORT_XLSX_PATH_NOT_FOUND`
- `BOOK_IMPORT_XLSX_INVALID_HEADER`
- `BOOK_IMPORT_XLSX_FAILED`

---

## 2. 사용자 API

### 2-1. 회원 생성
`POST /api/v1/users`

#### Request Body
```json
{
  "email": "user@example.com",
  "password": "password1234",
  "nickname": "miyo",
  "preferredGenreCodes": ["CLASSIC", "POETRY"]
}
```

#### Validation
- `email`: 필수, 이메일 형식
- `password`: 필수, 8~100자
- `nickname`: 필수, 최대 100자
- `preferredGenreCodes`: optional, 최대 5개

#### Response (`201 Created`)
```json
{
  "success": true,
  "message": "회원이 생성되었습니다.",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "miyo",
    "membershipActive": false,
    "status": "ACTIVE",
    "createdAt": "2026-07-02T04:10:54.801Z",
    "updatedAt": "2026-07-02T04:10:54.801Z"
  },
  "timestamp": "2026-07-02T04:10:54.900Z"
}
```

#### 프론트 참고
- 회원가입 시 간단한 설문(선호 장르 선택)을 같이 보내면 초기 추천 품질이 좋아집니다.
- `preferredGenreCodes`는 `GET /api/v1/genres`로 받은 `code` 값을 사용하면 됩니다.
- 설문 점수는 초기값일 뿐이고, 이후 `POST /api/v1/interactions` 데이터가 계속 누적되면서 추천이 보정됩니다.

---

### 2-2. 내 정보 조회
`GET /api/v1/users/me`

#### Headers
```http
X-USER-ID: 1
```

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "내 정보를 조회했습니다.",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "miyo",
    "membershipActive": false,
    "status": "ACTIVE",
    "createdAt": "2026-07-02T04:10:54.801Z",
    "updatedAt": "2026-07-02T04:10:54.801Z"
  },
  "timestamp": "2026-07-02T04:11:10.000Z"
}
```

---

### 2-3. 내 정보 수정
`PATCH /api/v1/users/me`

#### Headers
```http
X-USER-ID: 1
```

#### Request Body
```json
{
  "nickname": "new-nickname"
}
```

#### Validation
- `nickname`: 필수, 최대 100자

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "내 정보를 수정했습니다.",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "new-nickname",
    "membershipActive": false,
    "status": "ACTIVE",
    "createdAt": "2026-07-02T04:10:54.801Z",
    "updatedAt": "2026-07-02T04:12:00.000Z"
  },
  "timestamp": "2026-07-02T04:12:00.100Z"
}
```

---

## 3. 멤버십 API

### 3-1. 멤버십 ON/OFF 변경
`PATCH /api/v1/users/me/membership`

#### Headers
```http
X-USER-ID: 1
```

#### Request Body
```json
{
  "membershipActive": true
}
```

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "멤버십 상태를 변경했습니다.",
  "data": {
    "userId": 1,
    "membershipActive": true,
    "updatedAt": "2026-07-02T04:13:00.000Z"
  },
  "timestamp": "2026-07-02T04:13:00.100Z"
}
```

---

## 4. 도서 / 장르 API

### 4-1. 도서 목록 조회
`GET /api/v1/books`

#### Query Params
- `keyword` (optional): 제목 / 원제 / 저자명 검색
- `genreCode` (optional): 장르 코드 필터

#### Example
```http
GET /api/v1/books?keyword=Hong&genreCode=KDC_LITERATURE_KOREAN_LITERATURE
```

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "도서 목록을 조회했습니다.",
  "data": [
    {
      "id": 15054,
      "title": "Hong Gil Dong",
      "authorName": "Heo Gyun",
      "coverImageUrl": null,
      "publishedYear": null,
      "genres": [
        {
          "id": 281,
          "code": "KDC_LITERATURE_KOREAN_LITERATURE_KOREAN_FICTION_JOSEON_DYNASTY",
          "name": "KDC > literature > Korean Literature > Korean Fiction > Joseon Dynasty",
          "description": "엑셀 메타데이터에서 import된 분류: KDC > literature > Korean Literature > Korean Fiction > Joseon Dynasty"
        }
      ]
    }
  ],
  "timestamp": "2026-07-02T04:14:00.000Z"
}
```

---

### 4-2. 도서 상세 조회
`GET /api/v1/books/{bookId}`

#### Path Params
- `bookId`: 도서 ID

#### 동작 방식
- `MD_FULLTEXT` 또는 `contentAvailable=true` 도서: 기존 설명 그대로 반환
- `XLSX_METADATA` + `contentAvailable=false` 도서: 서버가 자동으로 AI 요약 보강을 시도
  - 이미 생성된 AI 요약이 있으면 재사용
  - 없고 OpenAI 설정이 활성화되어 있으면 생성 후 저장

#### Example
```http
GET /api/v1/books/15054
```

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "도서 상세를 조회했습니다.",
  "data": {
    "id": 15054,
    "title": "Hong Gil Dong",
    "authorName": "Heo Gyun",
    "description": "Original title: 홍길동전 | Translated language: German(Deutsch) | Classification: ...",
    "displayDescription": "이 작품은 한국 고전소설을 대표하는 텍스트로, 번역 독자에게도 한국 서사문학의 전통을 소개하는 입문서 역할을 합니다.",
    "aiSummary": "이 작품은 한국 고전소설을 대표하는 텍스트로, 번역 독자에게도 한국 서사문학의 전통을 소개하는 입문서 역할을 합니다.",
    "descriptionSource": "AI_GENERATED_SUMMARY",
    "originalTitle": "홍길동전",
    "country": "KOREA",
    "translatedLanguage": "German(Deutsch)",
    "authorNameKorean": null,
    "translator": null,
    "translationPublicationSupport": null,
    "publisher": null,
    "isbn": "9783746068350",
    "sourceBookId": "xlsx-isbn-9783746068350",
    "contentAvailable": false,
    "sourceType": "XLSX_METADATA",
    "coverImageUrl": null,
    "publishedYear": null,
    "active": true,
    "genres": [
      {
        "id": 281,
        "code": "KDC_LITERATURE_KOREAN_LITERATURE_KOREAN_FICTION_JOSEON_DYNASTY",
        "name": "KDC > literature > Korean Literature > Korean Fiction > Joseon Dynasty",
        "description": "엑셀 메타데이터에서 import된 분류: KDC > literature > Korean Literature > Korean Fiction > Joseon Dynasty"
      }
    ],
    "createdAt": "2026-07-02T04:12:05.833300Z",
    "updatedAt": "2026-07-02T04:12:05.833300Z"
  },
  "timestamp": "2026-07-02T04:14:30.000Z"
}
```

#### 프론트 참고
- `sourceType`
  - `XLSX_METADATA`: 메타데이터만 있는 도서
  - `MD_FULLTEXT`: 원문 마크다운까지 저장된 도서
  - `MANUAL`: 수동 등록 도서
- `contentAvailable`
  - `true`: 실제 원문/콘텐츠 있음
  - `false`: 메타데이터만 있음
- `displayDescription`
  - 프론트에서 사용자에게 우선 노출할 추천 설명 필드
  - 메타데이터 도서면 AI 요약을 우선 반영
  - 그 외에는 기존 `description`
- 외국인 전용 사이트 기준으로, 메타데이터 도서의 AI 요약은 **영어로 생성**됩니다.
- `aiSummary`
  - 실제 AI가 생성한 요약 텍스트
  - 없으면 `null`
- `descriptionSource`
  - `AI_GENERATED_SUMMARY`
  - `IMPORTED_METADATA_DESCRIPTION`
  - `CONTENT_BASED_DESCRIPTION`

---

### 4-3. 장르 목록 조회
`GET /api/v1/genres`

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "장르 목록을 조회했습니다.",
  "data": [
    {
      "id": 281,
      "code": "KDC_LITERATURE_KOREAN_LITERATURE_KOREAN_FICTION_JOSEON_DYNASTY",
      "name": "KDC > literature > Korean Literature > Korean Fiction > Joseon Dynasty",
      "description": "엑셀 메타데이터에서 import된 분류: ..."
    }
  ],
  "timestamp": "2026-07-02T04:15:00.000Z"
}
```

---

## 5. 상호작용 API

### 5-1. 상호작용 이벤트 저장
`POST /api/v1/interactions`

#### Headers
```http
X-USER-ID: 1
```

#### Request Body
```json
{
  "bookId": 15054,
  "interactionType": "VIEW",
  "viewDurationSeconds": 120,
  "progressPercent": 30,
  "sourceScreen": "book-detail"
}
```

#### Field Rules
- `bookId`: 필수
- `interactionType`: 필수
- `viewDurationSeconds`: optional, `0 이상`
- `progressPercent`: optional, `0~100`
- `sourceScreen`: optional, 최대 50자

#### interactionType enum
- `VIEW`
- `READ_START`
- `READ_COMPLETE`
- `LIKE`
- `UNLIKE`
- `BOOKMARK`
- `UNBOOKMARK`

#### 추가 검증 규칙
아래 타입은 `viewDurationSeconds` 또는 `progressPercent` 중 **하나 이상 필수**:
- `VIEW`
- `READ_START`
- `READ_COMPLETE`

#### Response (`201 Created`)
```json
{
  "success": true,
  "message": "상호작용 이벤트를 저장했습니다.",
  "data": {
    "eventId": 101,
    "userId": 1,
    "bookId": 15054,
    "interactionType": "VIEW",
    "viewDurationSeconds": 120,
    "progressPercent": 30,
    "sourceScreen": "book-detail",
    "createdAt": "2026-07-02T04:16:00.000Z"
  },
  "timestamp": "2026-07-02T04:16:00.100Z"
}
```

#### 프론트 참고
- 이 API 호출 시 사용자 선호 장르 점수도 내부적으로 반영됩니다.
- 이 API 호출 시 추천 캐시도 내부적으로 무효화됩니다.

---

## 6. 추천 API

### 6-1. 추천 피드 조회
`GET /api/v1/recommendations/feed`

#### Headers
```http
X-USER-ID: 1
```

#### Query Params
- `limit` (optional)
  - 기본값: `10`
  - 최대값: `50`

#### Example
```http
GET /api/v1/recommendations/feed?limit=10
```

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "추천 피드를 조회했습니다.",
  "data": {
    "userId": 1,
    "cacheKey": "rec:feed:user:1",
    "cached": true,
    "generatedAt": "2026-07-02T04:17:00.000Z",
    "expiresAt": "2026-07-02T04:32:00.000Z",
    "limit": 10,
    "items": [
      {
        "rank": 1,
        "bookId": 15054,
        "title": "Hong Gil Dong",
        "authorName": "Heo Gyun",
        "coverImageUrl": null,
        "genres": [
          "KDC_LITERATURE_KOREAN_LITERATURE_KOREAN_FICTION_JOSEON_DYNASTY"
        ],
        "reasonText": "사용자의 최근 관심 장르와 잘 맞는 작품입니다.",
        "keywordTags": ["classical", "novel", "joseon"],
        "score": 85,
        "sourceSignals": {
          "matchedGenres": [
            "kdc_literature_korean_literature_korean_fiction_joseon_dynasty"
          ],
          "recentReadBookIds": [15054, 15021, 14980],
          "reasonType": "PREGENERATED_BOOK_AI_TAG"
        }
      }
    ]
  },
  "timestamp": "2026-07-02T04:17:00.100Z"
}
```

#### 프론트 참고
- `cached=true`면 Redis/메모리 캐시에서 가져온 값
- `cached=false`면 새로 생성된 피드
- `cacheKey`는 디버깅용으로만 사용 권장
- `matchedGenres`는 **소문자 genre code** 기준

---

### 6-2. 추천 피드 강제 새로고침
`POST /api/v1/recommendations/refresh`

#### Headers
```http
X-USER-ID: 1
```

#### Request Body
`body`는 optional 입니다.

```json
{
  "limit": 12
}
```

또는 빈 body 가능:
```json
{}
```

#### Validation
- `limit`: optional, `1~50`

#### Response (`200 OK`)
응답 구조는 `GET /api/v1/recommendations/feed`와 동일합니다.

```json
{
  "success": true,
  "message": "추천 피드를 새로 생성했습니다.",
  "data": {
    "userId": 1,
    "cacheKey": "rec:feed:user:1",
    "cached": false,
    "generatedAt": "2026-07-02T04:18:00.000Z",
    "expiresAt": "2026-07-02T04:33:00.000Z",
    "limit": 12,
    "items": []
  },
  "timestamp": "2026-07-02T04:18:00.100Z"
}
```

---

### 6-3. 특정 책 추천 이유 조회
`GET /api/v1/recommendations/books/{bookId}/reason`

#### Headers
```http
X-USER-ID: 1
```

#### Path Params
- `bookId`: 추천 이유를 조회할 도서 ID

#### 설명
이 API는 아래 정보를 조합해서 추천 이유를 만듭니다.
- 사용자의 최근 읽은 책 최대 5권
- 해당 책과 겹치는 장르
- 누적된 사용자 장르 선호도
- 사전 생성된 AI 추천 문구(`BookAiTag.recommendationReason`)

#### Response (`200 OK`)
```json
{
  "success": true,
  "message": "추천 이유를 조회했습니다.",
  "data": {
    "userId": 1,
    "bookId": 15054,
    "bookTitle": "Hong Gil Dong",
    "personalizedReasonText": "최근 읽으신 '운수 좋은 날'과(와) 비슷한 관심 흐름이 보여 'Hong Gil Dong'을(를) 추천드려요. 특히 고전문학 장르 취향과 잘 맞습니다. 추가로, 최근 읽은 고전문학과 결이 비슷해 추천합니다.",
    "aiReasonText": "최근 읽은 고전문학과 결이 비슷해 추천합니다.",
    "keywordTags": ["classic", "short-story", "k-literature"],
    "matchedGenres": [
      {
        "code": "CLASSIC",
        "name": "고전문학"
      }
    ],
    "recentReadBooks": [
      {
        "bookId": 11,
        "title": "운수 좋은 날",
        "authorName": "현진건",
        "matchedGenreCodes": ["CLASSIC"]
      }
    ],
    "reasonType": "PREGENERATED_BOOK_AI_TAG",
    "generatedAt": "2026-07-02T04:21:00.000Z"
  },
  "timestamp": "2026-07-02T04:21:00.100Z"
}
```

#### 프론트 활용 포인트
- `personalizedReasonText`: UI에서 바로 보여줄 메인 추천 문구
- `aiReasonText`: 별도 서브 설명/툴팁으로 사용 가능
- `matchedGenres`: 왜 추천됐는지 태그 형태로 표시 가능
- `recentReadBooks`: "최근 읽은 책 기반 추천" UI 구성 가능

---

## 7. 관리자 도서 Import API

> 일반 사용자 프론트에서는 보통 직접 호출하지 않고, 운영/관리자 도구에서만 사용 권장

### 7-1. MD 도서 import
`POST /api/v1/admin/books/import-md`

#### Request Body
```json
{
  "importPath": "C:/data/md_books"
}
```

- `importPath` optional
- 비우면 서버 기본값 사용

#### Response
```json
{
  "success": true,
  "message": "마크다운 도서 import를 완료했습니다.",
  "data": {
    "importPath": "C:/data/md_books",
    "totalFiles": 82,
    "createdCount": 80,
    "updatedCount": 2,
    "importedAt": "2026-07-02T04:19:00.000Z",
    "books": [
      {
        "id": 1,
        "sourceBookId": "ka9999",
        "title": "Test Book",
        "genreCodes": ["고전소설", "민담"],
        "status": "CREATED"
      }
    ]
  },
  "timestamp": "2026-07-02T04:19:00.100Z"
}
```

---

### 7-2. XLSX 도서 import
`POST /api/v1/admin/books/import-xlsx`

#### Request Body
```json
{
  "importPath": "C:/data/import/translated_books.xlsx",
  "sheetName": "Books"
}
```

- `importPath` optional
- `sheetName` optional
- 둘 다 비우면 서버 기본값 / 첫 시트 사용

#### Response
```json
{
  "success": true,
  "message": "엑셀 도서 메타데이터 import를 완료했습니다.",
  "data": {
    "importPath": "C:/data/import/translated_books.xlsx",
    "sheetName": "Books",
    "totalRows": 7736,
    "importedRows": 7358,
    "createdCount": 7358,
    "updatedCount": 0,
    "importedAt": "2026-07-02T04:20:00.000Z",
    "books": [
      {
        "id": 15055,
        "sourceBookId": "xlsx-isbn-9786007332160",
        "title": "بال",
        "translatedLanguage": "Persian(فارسی)",
        "genreCodes": [
          "KDC_LITERATURE_KOREAN_LITERATURE_KOREAN_FICTION_20TH_CENTURY"
        ],
        "status": "CREATED"
      }
    ]
  },
  "timestamp": "2026-07-02T04:20:00.100Z"
}
```

---

## 8. 프론트 구현 시 주의사항

### 8-1. 현재 선호 장르 직접 수정 API는 없음
- 현재 추천 선호도는 `POST /api/v1/interactions` 호출을 통해 내부적으로 누적 반영됩니다.
- 즉, **취향 설정 화면용 별도 공개 API는 아직 없음**
- 필요하면 이후 `PreferenceController`를 추가 구현해야 합니다.

### 8-2. 추천 캐시 정책
- 키: `rec:feed:user:{userId}`
- 추천 피드 조회 시 캐시가 있으면 재사용
- 아래 이벤트 발생 시 내부적으로 캐시 무효화됨
  - 상호작용 저장
  - 추천 refresh 호출
  - 멤버십 변경

### 8-3. 응답 문자열 인코딩
- 다국어 제목/저자/언어 정보가 포함될 수 있으므로 프론트는 UTF-8 기준으로 처리해야 함
- 예: 한글, 아랍어, 프랑스어 악센트, 독일어 움라우트 포함 가능

---

## 9. 프론트 빠른 연결용 최소 흐름

### 회원가입
1. `POST /api/v1/users`
2. 응답의 `data.id`를 저장
3. 이후 사용자 API 호출 시 `X-USER-ID` 헤더에 넣기

### 책 목록/상세
1. `GET /api/v1/books`
2. 카드 클릭 시 `GET /api/v1/books/{bookId}`
3. 상세 화면에서는 `displayDescription`을 우선 렌더링

### 읽기 이벤트 저장
1. 상세 화면 진입/이탈 시 `POST /api/v1/interactions`
2. 좋아요/북마크 버튼도 같은 API 사용

### 추천 피드
1. `GET /api/v1/recommendations/feed`
2. 새로고침 버튼은 `POST /api/v1/recommendations/refresh`

---

## 10. 프론트용 enum 정리

### UserStatus
- `ACTIVE`
- `INACTIVE`

### InteractionType
- `VIEW`
- `READ_START`
- `READ_COMPLETE`
- `LIKE`
- `UNLIKE`
- `BOOKMARK`
- `UNBOOKMARK`

### sourceType
- `MANUAL`
- `MD_FULLTEXT`
- `XLSX_METADATA`

### import status
- `CREATED`
- `UPDATED`




