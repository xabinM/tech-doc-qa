헥사고날 아키텍처 규칙 위반을 검사한다.

$ARGUMENTS 가 있으면 해당 파일/디렉토리만, 없으면 `backend/src/` 전체를 검사한다.

**검사 항목:**

1. **계층 의존성 방향 위반**
   - `domain/` 에서 `infrastructure/`, `interfaces/`, `application/` import 금지
   - `application/` 에서 `interfaces/` import 금지
   - `infrastructure/` 에서 `interfaces/` import 금지

2. **domain 계층 오염**
   - `domain/` 파일에서 `@Entity` 외 Spring 어노테이션 사용 여부
   - 허용: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@ManyToOne`, `@OneToMany` 등 JPA 어노테이션
   - 금지: `@Service`, `@Component`, `@Repository`, `@Autowired`, `@Transactional` 등

3. **DTO 계층 위반**
   - `domain/` 또는 `application/` 에 DTO 클래스 존재 여부
   - DTO는 `interfaces/` 계층에만 허용

4. **Controller @Transactional 사용**
   - `interfaces/` 계층에 `@Transactional` 사용 여부

5. **하드코딩된 민감 정보**
   - URL, 비밀번호, secret key 하드코딩 여부

**출력 형식:**
- 위반 항목: 파일 경로:라인번호 + 위반 규칙 설명
- 위반 없으면 "아키텍처 규칙 위반 없음" 출력
