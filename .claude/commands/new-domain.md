$ARGUMENTS 이름으로 헥사고날 아키텍처 기반 도메인 스캐폴딩을 생성한다.

**인자 형식:** `{도메인명}` (예: `notification`, `document`)

**생성할 파일 목록** (basePackage = `com.example.backend`):

```
domain/{name}/
  {Name}.java                      ← Entity (@Entity, 순수 Java, Spring 어노테이션 금지)
  {Name}Repository.java            ← Repository 인터페이스 (순수 Java)

application/{name}/
  {Name}Service.java               ← 서비스 클래스
  port/
    {Name}Port.java                ← 외부 연동이 필요한 경우 포트 인터페이스

infrastructure/{name}/
  persistence/
    {Name}JpaRepository.java       ← Repository 구현체
    {Name}SpringDataJpaRepository.java ← Spring Data JPA 인터페이스

interfaces/{name}/
  {Name}Controller.java            ← Controller
  dto/
    {Name}Request.java
    {Name}Response.java
```

**규칙 준수:**
- domain 계층: `@Entity` 외 Spring 어노테이션 사용 금지
- 응답은 `ApiResponse<T>` 로 래핑
- 예외는 `CustomException` + `ErrorCode` 패턴
- Controller에 `@Transactional` 사용 금지

**생성 후:** 추가로 구현이 필요한 항목을 체크리스트로 출력한다.
