# 코드 스타일 규칙

- 생성자 주입: `@RequiredArgsConstructor` 사용, `@Autowired` 금지
- 매직 넘버/문자열은 상수 또는 enum으로 분리
- 메서드 길이 30줄 초과 시 분리 검토
- 패키지 구조 고정: `interfaces` / `application` / `domain` / `infrastructure` / `common`
