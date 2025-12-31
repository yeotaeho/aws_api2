package com.labzang.api.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;

/**
 * JWT 토큰 생성 및 검증 서비스
 * 
 * BASE64로 인코딩된 256비트 이상의 HMAC-SHA256 키를 사용합니다.
 * 키 생성은 JwtKeyGenerator 유틸리티를 사용하세요.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final SecretKey refreshSigningKey;

    @Value("${jwt.access-token-expiration:3600000}") // 기본 1시간
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:2592000000}") // 기본 30일
    private long refreshTokenExpiration;

    /**
     * JwtTokenProvider 생성자
     * BASE64로 인코딩된 시크릿 키를 디코딩하여 SecretKey로 변환합니다.
     * 
     * @param jwtSecret        BASE64로 인코딩된 JWT 시크릿 키 (최소 256비트)
     * @param jwtRefreshSecret BASE64로 인코딩된 JWT 리프레시 시크릿 키 (선택적, 없으면 jwtSecret 사용)
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.refresh-secret:${jwt.secret}}") String jwtRefreshSecret) {

        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT Secret이 설정되지 않았습니다. jwt.secret 환경 변수 또는 설정을 확인하세요.");
        }

        try {
            // BASE64 디코딩 시도
            byte[] keyBytes = decodeSecretKey(jwtSecret.trim());
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);

            // Refresh 토큰용 키 (별도로 설정되지 않으면 동일한 키 사용)
            if (jwtRefreshSecret != null && !jwtRefreshSecret.trim().isEmpty()
                    && !jwtRefreshSecret.equals(jwtSecret)) {
                byte[] refreshKeyBytes = decodeSecretKey(jwtRefreshSecret.trim());
                this.refreshSigningKey = Keys.hmacShaKeyFor(refreshKeyBytes);
            } else {
                this.refreshSigningKey = this.signingKey;
            }

        } catch (io.jsonwebtoken.security.WeakKeyException e) {
            throw new IllegalStateException(
                    "JWT Secret 키가 너무 짧습니다. 최소 256비트(32바이트)가 필요합니다. " +
                            "JwtKeyGenerator로 새로운 키를 생성하세요.",
                    e);
        }
    }

    /**
     * 시크릿 키 디코딩
     * BASE64 형식이면 디코딩하고, 그렇지 않으면 문자열을 바이트로 변환 후 SHA-256 해시로 확장
     * 
     * @param secret 시크릿 키 문자열 (BASE64 또는 일반 문자열)
     * @return 디코딩된 키 바이트 배열 (최소 32 bytes)
     */
    private byte[] decodeSecretKey(String secret) {
        try {
            // BASE64 디코딩 시도
            byte[] keyBytes = Decoders.BASE64.decode(secret);

            // BASE64 디코딩 성공 시 키 길이 확인
            if (keyBytes.length < 32) {
                // BASE64 키가 짧으면 SHA-256 해시로 확장
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    keyBytes = digest.digest(keyBytes);
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
                }
            }

            return keyBytes;

        } catch (IllegalArgumentException | io.jsonwebtoken.io.DecodingException e) {
            // BASE64가 아닌 경우: 일반 문자열로 처리
            // SHA-256 해시를 사용하여 항상 32 bytes (256 bits)로 확장
            try {
                byte[] stringBytes = secret.getBytes(StandardCharsets.UTF_8);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashedBytes = digest.digest(stringBytes);

                // 추가 보안: 원본 문자열과 해시를 결합하여 더 긴 키 생성
                // (기존 문자열이 매우 짧은 경우를 대비)
                if (stringBytes.length < 16) {
                    // 원본이 매우 짧으면 여러 번 해시
                    for (int i = 0; i < 2; i++) {
                        digest.reset();
                        hashedBytes = digest.digest(hashedBytes);
                    }
                }

                System.out.println("JWT Secret이 BASE64 형식이 아니므로 SHA-256 해시로 변환했습니다. " +
                        "프로덕션 환경에서는 JwtKeyGenerator로 생성한 BASE64 키를 사용하는 것을 권장합니다.");

                return hashedBytes;

            } catch (Exception ex) {
                throw new IllegalStateException(
                        "JWT Secret 키를 처리할 수 없습니다. 유효한 BASE64 문자열이거나 일반 문자열이어야 합니다.", ex);
            }
        }
    }

    /**
     * Access Token 생성
     * 
     * @param userId           사용자 ID
     * @param provider         OAuth2 제공자 (kakao, naver, google)
     * @param additionalClaims 추가 클레임 (nickname, email 등)
     * @return JWT Access Token
     */
    public String generateAccessToken(String userId, String provider, Map<String, Object> additionalClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId)
                .claim("provider", provider)
                .claim("type", "access")
                .claims(additionalClaims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Refresh Token 생성
     * 
     * @param userId   사용자 ID
     * @param provider OAuth2 제공자
     * @return JWT Refresh Token
     */
    public String generateRefreshToken(String userId, String provider) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userId)
                .claim("provider", provider)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(refreshSigningKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * JWT 토큰에서 사용자 ID 추출
     * 
     * @param token JWT 토큰
     * @return 사용자 ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * JWT 토큰에서 모든 클레임 추출
     * Access Token과 Refresh Token 모두에서 사용 가능합니다.
     * 
     * @param token JWT 토큰
     * @return 클레임 정보
     */
    public Claims getAllClaimsFromToken(String token) {
        // Access Token인지 Refresh Token인지 확인하여 적절한 키 사용
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // Access Token 검증 실패 시 Refresh Token 키로 시도
            return Jwts.parser()
                    .verifyWith(refreshSigningKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
    }

    /**
     * JWT 토큰 유효성 검증
     * 
     * @param token JWT 토큰
     * @return 유효 여부
     */
    public boolean validateToken(String token) {
        try {
            // Access Token 검증 시도
            try {
                Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token);
                return true;
            } catch (Exception e) {
                // Access Token 검증 실패 시 Refresh Token 검증 시도
                Jwts.parser()
                        .verifyWith(refreshSigningKey)
                        .build()
                        .parseSignedClaims(token);
                return true;
            }
        } catch (Exception e) {
            System.err.println("JWT 토큰 검증 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * JWT 토큰 만료 여부 확인
     * 
     * @param token JWT 토큰
     * @return 만료 여부
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}