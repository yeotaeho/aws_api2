package com.labzang.api.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

/**
 * JWT 시크릿 키 생성 유틸리티
 * 
 * 이 클래스는 한 번만 실행하여 BASE64로 인코딩된 JWT 시크릿 키를 생성합니다.
 * 생성된 키는 application.yaml 또는 환경 변수에 설정하여 사용합니다.
 * 
 * 사용 방법:
 * 1. 이 클래스의 main 메서드를 실행
 * 2. 출력된 BASE64 문자열을 복사
 * 3. .env 파일 또는 application.yaml에 설정:
 * JWT_SECRET=<생성된_BASE64_문자열>
 * 
 * 주의: 프로덕션 환경에서는 각 환경별로 다른 키를 사용하세요.
 */
public class JwtKeyGenerator {

    /**
     * HS256 알고리즘용 256비트 시크릿 키를 생성하고 BASE64로 인코딩하여 출력합니다.
     * 
     * @param args 명령줄 인수 (사용하지 않음)
     */
    public static void main(String[] args) {
        System.out.println("=== JWT Secret Key Generator ===\n");

        // Access Token용 키 생성
        SecretKey accessKey = Jwts.SIG.HS256.key().build();
        String accessKeyBase64 = Encoders.BASE64.encode(accessKey.getEncoded());

        // Refresh Token용 키 생성 (선택적, 별도 키 사용 시)
        SecretKey refreshKey = Jwts.SIG.HS256.key().build();
        String refreshKeyBase64 = Encoders.BASE64.encode(refreshKey.getEncoded());

        System.out.println("Access Token용 JWT Secret (BASE64):");
        System.out.println(accessKeyBase64);
        System.out.println();

        System.out.println("Refresh Token용 JWT Secret (BASE64) - 선택적:");
        System.out.println(refreshKeyBase64);
        System.out.println();

        System.out.println("=== 설정 예시 ===");
        System.out.println();
        System.out.println("# .env 파일에 추가:");
        System.out.println("JWT_SECRET=" + accessKeyBase64);
        System.out.println("# 또는 Refresh Token을 별도로 사용하는 경우:");
        System.out.println("JWT_SECRET=" + accessKeyBase64);
        System.out.println("JWT_REFRESH_SECRET=" + refreshKeyBase64);
        System.out.println();
        System.out.println("# application.yaml에 추가 (선택적):");
        System.out.println("jwt:");
        System.out.println("  secret: " + accessKeyBase64);
        System.out.println("  refresh-secret: " + refreshKeyBase64 + "  # 선택적, 없으면 secret 사용");
        System.out.println();

        System.out.println("주의: 생성된 키는 안전하게 보관하고, 프로덕션 환경에서는 절대 공유하지 마세요!");
    }
}
