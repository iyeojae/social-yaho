# social-yaho 실행 & 데이터 적재 가이드

이 문서는 **처음부터 끝까지 순서대로 명령어만 따라 치면** Docker로 백엔드를 띄우고,
현재 준비된 데이터인 **엑셀 책 목록(`TRANSLATEDBOOKS_20260702032206.xlsx`)** 을 DB에 적재하는 방법을 정리한 문서입니다.

> 현재 상태 기준
>
> - `xlsx` import: **바로 가능**
> - `md_books` import: **각 md 파일에 `**장르:** ...` 메타데이터를 넣은 뒤 실행해야 함**

---

## 0. 사전 체크

아래 3개가 준비되어 있어야 합니다.

- Docker Desktop 실행 중
- Java 17 설치
- 현재 프로젝트 폴더에 아래 파일들이 존재
  - `docker-compose.yml`
  - `Dockerfile`
  - `TRANSLATEDBOOKS_20260702032206.xlsx`
  - `md_books/`

---

## 1. 프로젝트 폴더로 이동

```powershell
Set-Location "C:\Users\miyom\IdeaProjects\social-yaho"
```

---

## 2. Docker Desktop 켜기

윈도우에서 **Docker Desktop**을 먼저 실행합니다.

정상 실행 여부 확인:

```powershell
docker version
```

정상이라면 client/server 정보가 출력됩니다.

만약 에러가 나면:
- Docker Desktop이 안 켜졌거나
- 엔진이 아직 시작 중인 상태입니다.

잠시 기다렸다가 다시 실행하세요.

---

## 3. Spring Boot JAR 빌드

```powershell
.\gradlew.bat clean bootJar --no-daemon
```

정상 완료되면 `build/libs/` 아래에 jar가 생성됩니다.

---

## 4. Docker Compose로 전체 스택 실행

```powershell
docker compose up --build -d
```

이 명령으로 아래 3개가 같이 올라갑니다.

- Spring Boot app
- MySQL
- Redis

---

## 5. 컨테이너 상태 확인

```powershell
docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
```

정상 목표 상태:

- `klit-spring-app`
- `klit-mysql`
- `klit-redis`

모두 `Up` 상태면 좋습니다.

---

## 6. 앱이 DB보다 먼저 떠서 죽었는지 확인

현재 `docker-compose.yml`은 `depends_on`만 사용하므로,
처음 실행 시 Spring Boot가 MySQL 준비 전에 먼저 붙으려고 하다가 실패할 수 있습니다.

앱 로그 확인:

```powershell
docker logs klit-spring-app --tail 100
```

만약 DB 연결 에러가 보이면, 앱 컨테이너만 다시 시작하세요.

```powershell
docker start klit-spring-app
```

그리고 다시 상태 확인:

```powershell
docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"
```

---

## 7. 엑셀 메타데이터 책 목록 import 실행

현재 바로 넣을 수 있는 데이터는 엑셀 파일입니다.

컨테이너 내부에서는 이 파일이 아래 경로로 연결되어 있습니다.

- `/data/import/translated_books.xlsx`

아래 명령 실행:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/books/import-xlsx" `
  -ContentType "application/json" `
  -Body "{}"
```

정상이라면 응답에 아래와 비슷한 정보가 나옵니다.

- `totalRows`
- `importedRows`
- `createdCount`
- `updatedCount`

처음 실행 시 보통 `createdCount`가 크게 나오고,
같은 파일을 다시 실행하면 `updatedCount`가 증가합니다.

---

## 8. 엑셀 import 결과 DB에서 확인

### 전체 책 수 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT COUNT(*) AS total_books FROM literature_works;"
```

### 메타데이터 책 일부 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT id, source_book_id, title, original_title, author_name, translated_language, isbn, source_type, content_available FROM literature_works ORDER BY id DESC LIMIT 20;"
```

### 장르 매핑 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT lw.title, g.code, g.name FROM literature_works lw JOIN literature_work_genres lwg ON lw.id = lwg.literature_work_id JOIN genres g ON g.id = lwg.genre_id ORDER BY lw.id DESC LIMIT 30;"
```

---

## 9. API로 책 상세 확인

아래 명령으로 상세 API 응답을 확인할 수 있습니다.

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/books/1"
```

확인 포인트:

- `originalTitle`
- `translatedLanguage`
- `authorNameKorean`
- `publisher`
- `isbn`
- `sourceBookId`
- `contentAvailable`
- `sourceType`

`sourceType`이 `XLSX_METADATA`이고 `contentAvailable=false`면,
본문은 없고 메타데이터만 들어간 책입니다.

---

## 10. md_books import는 지금 바로 하지 말 것

`md_books`는 아직 **각 파일에 장르 메타데이터가 반영되지 않은 상태**라면 import를 하면 실패할 수 있습니다.

현재 md import는 각 파일 안에 아래 형식이 있어야 합니다.

```md
**장르:** Poetry, Folk Tale, Classic Novel
```

또는

```md
**책 장르:** Poetry, Folk Tale, Classic Novel
```

### 아직 md 파일 장르를 안 넣었다면
지금은 **엑셀 import만 먼저 진행**하세요.

---

## 11. md 파일 장르 반영 후 import하는 명령어

md 파일에 장르를 다 넣은 다음에만 실행하세요.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/admin/books/import-md" `
  -ContentType "application/json" `
  -Body "{}"
```

---

## 12. md import 결과 확인

### 본문이 있는 책 수 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT COUNT(*) AS md_fulltext_books FROM literature_works WHERE source_type = 'MD_FULLTEXT';"
```

### 메타데이터만 있는 책 수 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT COUNT(*) AS xlsx_metadata_books FROM literature_works WHERE source_type = 'XLSX_METADATA';"
```

### 본문 저장 길이 확인

```powershell
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT source_book_id, LENGTH(content_markdown) AS content_size FROM literature_works WHERE source_type = 'MD_FULLTEXT' ORDER BY content_size DESC LIMIT 10;"
```

---

## 13. 추천 캐시/Redis까지 포함한 기본 실행 확인

Redis까지 같이 떠 있으므로, 추천 API도 호출 가능합니다.

예시:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/recommendations/feed?limit=10" `
  -Headers @{"X-USER-ID"="1"}
```

> 주의: 추천 결과가 의미 있으려면
> - user 데이터
> - interaction 데이터
> - genre 매핑
> 이 어느 정도 있어야 합니다.

---

## 14. 자주 쓰는 로그 확인 명령어

### 앱 로그

```powershell
docker logs klit-spring-app --tail 200
```

### MySQL 로그

```powershell
docker logs klit-mysql --tail 200
```

### Redis 로그

```powershell
docker logs klit-redis --tail 200
```

---

## 15. 종료 명령어

### 컨테이너만 종료

```powershell
docker compose down
```

### 볼륨까지 제거하고 완전 초기화

```powershell
docker compose down -v
```

> `-v`를 쓰면 MySQL 데이터도 같이 지워집니다.

---

## 16. 처음부터 다시 하고 싶을 때

완전 초기화 후 재시작 순서:

```powershell
Set-Location "C:\Users\miyom\IdeaProjects\social-yaho"
docker compose down -v
.\gradlew.bat clean bootJar --no-daemon
docker compose up --build -d
docker start klit-spring-app
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/admin/books/import-xlsx" -ContentType "application/json" -Body "{}"
```

---

## 17. 가장 짧은 실전 순서 요약

### 지금 당장 해야 하는 최소 순서

```powershell
Set-Location "C:\Users\miyom\IdeaProjects\social-yaho"
docker version
.\gradlew.bat clean bootJar --no-daemon
docker compose up --build -d
docker start klit-spring-app
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/admin/books/import-xlsx" -ContentType "application/json" -Body "{}"
docker exec -i klit-mysql mysql -uroot -proot -D klit_db -e "SELECT COUNT(*) AS total_books FROM literature_works;"
```

---

## 18. 현재 추천 운영 방식 요약

- `xlsx` 책: 메타데이터만 있음
  - `sourceType = XLSX_METADATA`
  - `contentAvailable = false`
- `md` 책: 본문까지 있음
  - `sourceType = MD_FULLTEXT`
  - `contentAvailable = true`

즉 지금은:
- **엑셀로 대규모 후보군 확보 가능**
- **md는 장르 입력 후 본문형 도서로 추가 가능**

---

## 19. 문제가 생기면 제일 먼저 볼 것

1. Docker Desktop 켜졌는지
2. `docker ps`에서 3개 컨테이너가 떠 있는지
3. `klit-spring-app` 로그에 DB 연결 실패가 있는지
4. 엑셀 import API 호출이 200 응답인지
5. MySQL에서 `literature_works` row 수가 증가했는지

---

이 문서대로 하면 **지금 기준으로는 엑셀 import까지는 바로 진행 가능**하고,
**md_books는 장르 메타데이터 반영 후에 이어서 import**하면 됩니다.

