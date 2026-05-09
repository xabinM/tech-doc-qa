# API 응답 규칙

- 모든 응답은 `ApiResponse<T>` 래핑
- 성공 응답 구조: `{"success": true, "message": "...", "data": {...}}` (data 없으면 생략)
- 실패 응답 구조: `{"success": false, "error": {"code": "...", "message": "..."}}`
- 성공 message는 한국어로 작성, 모든 엔드포인트에 필수
- 성공: 200(조회/수정), 201(생성), 204(삭제)
- 에러: 400(입력오류), 401(인증없음), 403(권한없음), 404(리소스없음), 409(중복), 429(Rate Limit), 503(외부 서비스 장애)
- 에러는 `CustomException` + `ErrorCode`, `GlobalExceptionHandler`에서 일괄 처리
- 페이지네이션은 Cursor 기반, offset 기반 금지
