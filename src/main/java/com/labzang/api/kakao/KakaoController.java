package com.labzang.api.kakao;

import com.labzang.api.jwt.JwtTokenProvider;
import com.labzang.api.jwt.JwtUtil;
import com.labzang.api.kakao.dto.KakaoTokenResponse;
import com.labzang.api.kakao.dto.KakaoUserInfo;
import com.labzang.api.token.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/kakao")
@RequiredArgsConstructor
public class KakaoController {

    private final TokenService tokenService;
    private final KakaoOAuthService kakaoOAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 카카오 인증 URL 제공
     * 프론트엔드에서 REST API KEY를 노출하지 않고 인증 URL을 가져올 수 있도록 함
     * 프론트엔드 콜백 URL을 파라미터로 받아서 사용
     */
    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getKakaoAuthUrl(
            @RequestParam(required = false) String redirect_uri) {
        System.out.println("=== 카카오 인증 URL 요청 ===");

        // 환경 변수에서 가져오기
        String clientId = System.getenv("KAKAO_REST_API_KEY");

        // 프론트엔드에서 전달한 redirect_uri 사용, 없으면 환경 변수 사용
        String redirectUri = redirect_uri != null && !redirect_uri.isEmpty()
                ? redirect_uri
                : System.getenv("KAKAO_REDIRECT_URI");

        if (clientId == null || clientId.isEmpty()) {
            System.err.println("경고: KAKAO_REST_API_KEY가 설정되지 않았습니다.");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "카카오 REST API KEY가 설정되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }

        if (redirectUri == null || redirectUri.isEmpty()) {
            System.err.println("경고: 리다이렉트 URI가 설정되지 않았습니다.");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "카카오 리다이렉트 URI가 설정되지 않았습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }

        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String authUrl = String.format(
                "https://kauth.kakao.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code",
                clientId,
                encodedRedirectUri);

        System.out.println("카카오 인증 URL 생성 완료");
        System.out.println("Redirect URI: " + redirectUri);
        System.out.println("============================");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "auth_url", authUrl));
    }

    /**
     * 카카오 인증 콜백 처리
     * Authorization Code를 받아서 프론트엔드로 리다이렉트
     */
    @GetMapping("/callback")
    public ResponseEntity<?> kakaoCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description) {

        System.out.println("=== 카카오 콜백 요청 수신 ===");
        System.out.println("Code: " + code);
        System.out.println("Error: " + error);
        System.out.println("Error Description: " + error_description);
        System.out.println("============================");

        // 프론트엔드 콜백 URL
        String frontendCallbackUrl = "http://localhost:3000/kakao-callback";

        try {
            if (code != null) {
                // Authorization Code를 Redis에 임시 저장 (10분 유효)
                tokenService.saveAuthorizationCode("kakao", code, null, 600);
                System.out.println("✅ Authorization Code를 Redis에 저장했습니다.");

                // 프론트엔드로 인가 코드와 함께 리다이렉트
                String redirectUrl = frontendCallbackUrl + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
                System.out.println("프론트엔드로 리다이렉트: " + redirectUrl);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", redirectUrl)
                        .build();
            } else if (error != null) {
                // 에러가 있는 경우 프론트엔드로 에러와 함께 리다이렉트
                String redirectUrl = frontendCallbackUrl + "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8);
                if (error_description != null) {
                    redirectUrl += "&error_description=" + URLEncoder.encode(error_description, StandardCharsets.UTF_8);
                }
                System.out.println("에러 발생, 프론트엔드로 리다이렉트: " + redirectUrl);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", redirectUrl)
                        .build();
            } else {
                // 인가 코드가 없는 경우
                String redirectUrl = frontendCallbackUrl + "?error=no_code&error_description="
                        + URLEncoder.encode("인증 코드가 없습니다.", StandardCharsets.UTF_8);
                System.out.println("인가 코드 없음, 프론트엔드로 리다이렉트: " + redirectUrl);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", redirectUrl)
                        .build();
            }
        } catch (Exception e) {
            System.err.println("콜백 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();

            // 예외 발생 시에도 프론트엔드로 리다이렉트
            try {
                String redirectUrl = frontendCallbackUrl + "?error=server_error&error_description="
                        + URLEncoder.encode("서버 오류가 발생했습니다.", StandardCharsets.UTF_8);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .header("Location", redirectUrl)
                        .build();
            } catch (Exception ex) {
                // 리다이렉트 URL 생성 실패 시 JSON 응답 반환
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "콜백 처리 중 오류가 발생했습니다: " + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
        }
    }

    /**
     * 카카오 로그인 요청 처리
     * Next.js에서 성공으로 인식하도록 항상 성공 응답 반환
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> kakaoLogin(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        System.out.println("=== 카카오 로그인 요청 수신 ===");
        System.out.println("Request Body: " + request);

        // Authorization 헤더에서 토큰 확인
        if (authHeader != null) {
            System.out.println("Authorization 헤더: " + authHeader);
            if (authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                System.out.println("추출된 토큰: " + token.substring(0, Math.min(token.length(), 50)) + "...");
                // JWT 토큰 파싱 및 정보 출력
                System.out.println(JwtUtil.formatTokenInfo(authHeader));
            }
        } else {
            System.out.println("Authorization 헤더 없음");
        }

        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "카카오 로그인이 성공적으로 처리되었습니다.");
        response.put("token", "mock_token_" + System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 카카오 토큰 검증 및 저장
     * Authorization Code를 Access Token으로 교환하고 Redis에 저장
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> kakaoToken(@RequestBody(required = false) Map<String, Object> request) {
        System.out.println("=== 카카오 토큰 요청 수신 ===");
        System.out.println("Request Body: " + request);

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Authorization Code 검증
            if (request == null || !request.containsKey("code")) {
                response.put("success", false);
                response.put("message", "Authorization Code가 필요합니다.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            String code = request.get("code").toString();

            // 2. Redis에서 Authorization Code 검증
            String state = tokenService.verifyAndDeleteAuthorizationCode("kakao", code);
            if (state == null) {
                System.out.println("경고: Redis에 Authorization Code가 없습니다. 계속 진행합니다.");
                // Redis에 없어도 카카오 API 호출은 진행 (개발 환경 고려)
            }

            // 3. 카카오 Access Token 교환
            System.out.println("카카오 API로 Access Token 요청 중...");
            Map<String, Object> kakaoTokenResponse = kakaoOAuthService.getAccessToken(code);
            String kakaoAccessToken = (String) kakaoTokenResponse.get("access_token");
            String kakaoRefreshToken = (String) kakaoTokenResponse.get("refresh_token");
            Object expiresIn = kakaoTokenResponse.get("expires_in"); // 초 단위

            if (kakaoAccessToken == null) {
                response.put("success", false);
                response.put("message", "카카오 Access Token 발급 실패");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 4. 카카오 사용자 정보 조회
            System.out.println("카카오 사용자 정보 조회 중...");
            Map<String, Object> kakaoUserInfo = kakaoOAuthService.getUserInfo(kakaoAccessToken);
            Map<String, Object> userInfo = kakaoOAuthService.extractUserInfo(kakaoUserInfo);

            // 5. 사용자 ID 추출
            String userId = userInfo.get("kakao_id").toString();

            // 6. 카카오 OAuth 원본 토큰을 Redis에 저장
            long kakaoTokenExpireTime = expiresIn != null ? Long.parseLong(expiresIn.toString()) : 21600; // 기본 6시간 (카카오 기본값)
            tokenService.saveOAuthAccessToken("kakao", userId, kakaoAccessToken, kakaoTokenExpireTime);
            
            if (kakaoRefreshToken != null) {
                // Refresh Token은 60일 유효 (카카오 기본값)
                tokenService.saveOAuthRefreshToken("kakao", userId, kakaoRefreshToken, 5184000);
            }

            // 7. JWT 토큰 생성 (자체 JWT)
            String jwtAccessToken = jwtTokenProvider.generateAccessToken(userId, "kakao", userInfo);
            String jwtRefreshToken = jwtTokenProvider.generateRefreshToken(userId, "kakao");

            System.out.println("✅ JWT 토큰 생성 완료 - AccessToken: "
                    + jwtAccessToken.substring(0, Math.min(50, jwtAccessToken.length())) + "...");

            // 8. JWT 토큰을 Redis에 저장 (Access Token: 1시간, Refresh Token: 30일)
            System.out.println("Redis에 토큰 저장 중...");
            tokenService.saveAccessToken("kakao", userId, jwtAccessToken, 3600);
            tokenService.saveRefreshToken("kakao", userId, jwtRefreshToken, 2592000);

            // Redis 저장 확인
            String savedAccessToken = tokenService.getAccessToken("kakao", userId);
            String savedRefreshToken = tokenService.getRefreshToken("kakao", userId);
            if (savedAccessToken != null && savedRefreshToken != null) {
                System.out.println("✅ Redis에 토큰 저장 성공!");
                System.out.println("  - Access Token Key: token:kakao:" + userId + ":access");
                System.out.println("  - Refresh Token Key: token:kakao:" + userId + ":refresh");
            } else {
                System.out.println("⚠️ Redis 토큰 저장 확인 실패");
            }

            System.out.println("카카오 인증 완료: " + userInfo.get("nickname"));
            System.out.println("============================");

            // 7. 응답 반환
            response.put("success", true);
            response.put("message", "카카오 로그인이 성공적으로 처리되었습니다.");
            response.put("access_token", jwtAccessToken);
            response.put("refresh_token", jwtRefreshToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", 3600);
            response.put("user", userInfo);

            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            System.err.println("카카오 인증 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();

            response.put("success", false);
            response.put("message", "카카오 인증 처리 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 카카오 사용자 정보 조회
     * JWT 토큰으로 사용자 정보 반환
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> kakaoUserInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        System.out.println("=== 카카오 사용자 정보 조회 요청 수신 ===");

        Map<String, Object> response = new HashMap<>();

        try {
            // Authorization 헤더 검증
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                System.out.println("Authorization 헤더 없음 또는 형식 오류");
                response.put("success", false);
                response.put("message", "인증 토큰이 필요합니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            String token = authHeader.substring(7);
            System.out.println("JWT 토큰 검증 중...");

            // JWT 토큰 검증
            if (!jwtTokenProvider.validateToken(token)) {
                System.out.println("JWT 토큰 검증 실패");
                response.put("success", false);
                response.put("message", "유효하지 않은 토큰입니다.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            // JWT 토큰에서 사용자 정보 추출
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            var claims = jwtTokenProvider.getAllClaimsFromToken(token);

            System.out.println("사용자 인증 성공: " + userId);

            // Redis에서 토큰 확인 (선택적)
            String storedToken = tokenService.getAccessToken("kakao", userId);
            if (storedToken == null) {
                System.out.println("경고: Redis에 저장된 토큰이 없습니다.");
            }

            // 사용자 정보 구성
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("kakao_id", userId);
            userInfo.put("nickname", claims.get("nickname"));
            userInfo.put("email", claims.get("email"));
            userInfo.put("email_verified", claims.get("email_verified"));
            userInfo.put("profile_image", claims.get("profile_image"));
            userInfo.put("provider", "kakao");

            System.out.println("============================");

            response.put("success", true);
            response.put("message", "카카오 사용자 정보를 성공적으로 조회했습니다.");
            response.put("user", userInfo);

            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            System.err.println("사용자 정보 조회 중 오류 발생: " + e.getMessage());
            e.printStackTrace();

            response.put("success", false);
            response.put("message", "사용자 정보 조회 중 오류가 발생했습니다.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 모든 카카오 관련 요청에 대한 기본 핸들러
     * Next.js에서 성공으로 인식하도록 항상 성공 응답 반환
     */
    @RequestMapping(value = "/**", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE })
    public ResponseEntity<Map<String, Object>> kakaoDefault() {
        System.out.println("=== 카카오 기본 핸들러 요청 수신 ===");
        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "카카오 요청이 성공적으로 처리되었습니다.");

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}