# 프론트엔드 API 연동 가이드

## 📋 목차
1. [기본 정보](#기본-정보)
2. [구글 OAuth 인증](#구글-oauth-인증)
3. [카카오 OAuth 인증](#카카오-oauth-인증)
4. [JWT 토큰 사용](#jwt-토큰-사용)
5. [에러 처리](#에러-처리)
6. [예제 코드](#예제-코드)

---

## 기본 정보

### Base URL
```
http://localhost:8080
```

### CORS 설정
- 허용된 Origin: `http://localhost:3000`, `http://127.0.0.1:3000`
- 허용된 메서드: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`
- Credentials: `true` (쿠키 포함 가능)

### API 라우팅 규칙
- 인증 관련: `/api/auth/**` → `oauthservice`로 라우팅
- 카카오 OAuth: `/oauth2/kakao/**` → `oauthservice`로 라우팅
- 사용자 관련: `/api/users/**` → `userservice`로 라우팅

---

## 구글 OAuth 인증

### 플로우 개요
```
1. 프론트엔드 → GET /api/auth/google/auth-url
2. 사용자 → 구글 로그인 페이지로 리다이렉트
3. 구글 → GET /api/auth/google/callback?code=xxx
4. 백엔드 → JWT 토큰 생성 후 프론트엔드로 리다이렉트
5. 프론트엔드 → URL에서 토큰 추출하여 저장
```

### 1. 인증 URL 가져오기

**엔드포인트:** `GET /api/auth/google/auth-url`

**요청:**
```typescript
const response = await fetch('http://localhost:8080/api/auth/google/auth-url');
const data = await response.json();
```

**응답:**
```json
{
  "success": true,
  "auth_url": "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=...&redirect_uri=...&scope=openid%20profile%20email&state=..."
}
```

**사용 예시:**
```typescript
const response = await fetch('http://localhost:8080/api/auth/google/auth-url');
const { auth_url } = await response.json();
window.location.href = auth_url; // 구글 로그인 페이지로 이동
```

### 2. 콜백 처리

**엔드포인트:** `GET /api/auth/google/callback`

구글 인증 완료 후 자동으로 호출됩니다. 백엔드에서 JWT 토큰을 생성한 후 프론트엔드로 리다이렉트합니다.

**리다이렉트 URL 형식:**
- 성공: `http://localhost:3000?token=<JWT_ACCESS_TOKEN>&refresh_token=<JWT_REFRESH_TOKEN>`
- 실패: `http://localhost:3000?error=<에러메시지>`

**프론트엔드 콜백 페이지 예시:**
```typescript
// pages/callback.tsx 또는 app/callback/page.tsx
'use client';

import { useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';

export default function CallbackPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  useEffect(() => {
    const token = searchParams.get('token');
    const refreshToken = searchParams.get('refresh_token');
    const error = searchParams.get('error');

    if (error) {
      console.error('인증 실패:', error);
      router.push('/login?error=' + encodeURIComponent(error));
      return;
    }

    if (token) {
      // 토큰을 localStorage 또는 쿠키에 저장
      localStorage.setItem('accessToken', token);
      if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken);
      }
      
      // 메인 페이지로 이동
      router.push('/');
    }
  }, [searchParams, router]);

  return <div>인증 처리 중...</div>;
}
```

---

## 카카오 OAuth 인증

### 플로우 개요
```
1. 프론트엔드 → GET /oauth2/kakao/auth-url?redirect_uri=...
2. 사용자 → 카카오 로그인 페이지로 리다이렉트
3. 카카오 → GET /oauth2/kakao/callback?code=xxx
4. 백엔드 → 프론트엔드로 인가 코드와 함께 리다이렉트
5. 프론트엔드 → POST /api/auth/kakao/token (인가 코드로 JWT 토큰 교환)
```

### 1. 인증 URL 가져오기

**엔드포인트:** `GET /oauth2/kakao/auth-url`

**쿼리 파라미터:**
- `redirect_uri` (선택): 프론트엔드 콜백 URL (없으면 서버 기본값 사용)

**요청:**
```typescript
const redirectUri = encodeURIComponent('http://localhost:3000/kakao-callback');
const response = await fetch(
  `http://localhost:8080/oauth2/kakao/auth-url?redirect_uri=${redirectUri}`
);
const data = await response.json();
```

**응답:**
```json
{
  "success": true,
  "auth_url": "https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code"
}
```

### 2. 콜백 처리

**엔드포인트:** `GET /oauth2/kakao/callback`

카카오 인증 완료 후 자동으로 호출됩니다. 백엔드에서 인가 코드를 받아 프론트엔드로 리다이렉트합니다.

**리다이렉트 URL 형식:**
- 성공: `http://localhost:3000/kakao-callback?code=<AUTHORIZATION_CODE>`
- 실패: `http://localhost:3000/kakao-callback?error=<에러코드>&error_description=<에러설명>`

**프론트엔드 콜백 페이지:**
```typescript
// pages/kakao-callback.tsx
'use client';

import { useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';

export default function KakaoCallbackPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  useEffect(() => {
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    if (error) {
      console.error('카카오 인증 실패:', error);
      router.push('/login?error=' + encodeURIComponent(error));
      return;
    }

    if (code) {
      // 인가 코드로 JWT 토큰 교환
      exchangeToken(code);
    }
  }, [searchParams, router]);

  const exchangeToken = async (code: string) => {
    try {
      const response = await fetch('http://localhost:8080/api/auth/kakao/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ code }),
      });

      const data = await response.json();

      if (data.success) {
        // JWT 토큰 저장
        localStorage.setItem('accessToken', data.access_token);
        if (data.refresh_token) {
          localStorage.setItem('refreshToken', data.refresh_token);
        }
        
        router.push('/');
      } else {
        console.error('토큰 교환 실패:', data.message);
        router.push('/login?error=' + encodeURIComponent(data.message));
      }
    } catch (error) {
      console.error('토큰 교환 중 오류:', error);
      router.push('/login?error=토큰_교환_실패');
    }
  };

  return <div>카카오 인증 처리 중...</div>;
}
```

### 3. 토큰 교환

**엔드포인트:** `POST /api/auth/kakao/token`

**요청 Body:**
```json
{
  "code": "카카오에서_받은_인가_코드"
}
```

**응답 (성공):**
```json
{
  "success": true,
  "access_token": "JWT_ACCESS_TOKEN",
  "refresh_token": "JWT_REFRESH_TOKEN",
  "user_id": "카카오_사용자_ID",
  "user": {
    "id": "카카오_사용자_ID",
    "nickname": "사용자_닉네임",
    "email": "user@example.com"
  }
}
```

**응답 (실패):**
```json
{
  "success": false,
  "message": "에러 메시지"
}
```

---

## JWT 토큰 사용

### 토큰 저장
```typescript
// 로그인 성공 후
localStorage.setItem('accessToken', token);
localStorage.setItem('refreshToken', refreshToken);
```

### API 요청 시 토큰 포함
```typescript
const token = localStorage.getItem('accessToken');

const response = await fetch('http://localhost:8080/api/users/profile', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
});
```

### 토큰 만료 처리
```typescript
async function fetchWithToken(url: string, options: RequestInit = {}) {
  let token = localStorage.getItem('accessToken');
  
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`,
    },
  });

  // 401 Unauthorized - 토큰 만료
  if (response.status === 401) {
    // Refresh Token으로 새 Access Token 발급 시도
    const newToken = await refreshAccessToken();
    if (newToken) {
      // 재시도
      return fetch(url, {
        ...options,
        headers: {
          ...options.headers,
          'Authorization': `Bearer ${newToken}`,
        },
      });
    } else {
      // Refresh Token도 만료된 경우 로그인 페이지로
      localStorage.clear();
      window.location.href = '/login';
      throw new Error('인증이 만료되었습니다.');
    }
  }

  return response;
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;

  try {
    const response = await fetch('http://localhost:8080/api/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });

    const data = await response.json();
    if (data.success) {
      localStorage.setItem('accessToken', data.access_token);
      return data.access_token;
    }
  } catch (error) {
    console.error('토큰 갱신 실패:', error);
  }

  return null;
}
```

---

## 에러 처리

### 일반적인 에러 응답 형식
```json
{
  "success": false,
  "message": "에러 메시지"
}
```

### HTTP 상태 코드
- `200 OK`: 성공
- `400 Bad Request`: 잘못된 요청 (필수 파라미터 누락 등)
- `401 Unauthorized`: 인증 실패 (토큰 만료, 유효하지 않은 토큰)
- `500 Internal Server Error`: 서버 내부 오류

### 콜백 URL 에러 파라미터
구글/카카오 콜백에서 에러가 발생한 경우:
- `error`: 에러 코드 또는 메시지
- `error_description`: 상세 에러 설명

**예시:**
```
http://localhost:3000?error=인증_처리_중_오류가_발생했습니다.
http://localhost:3000/kakao-callback?error=access_denied&error_description=사용자가_인증을_거부했습니다.
```

---

## 예제 코드

### React/Next.js 전체 예시

```typescript
// hooks/useAuth.ts
'use client';

import { useState, useEffect } from 'react';

interface AuthTokens {
  accessToken: string | null;
  refreshToken: string | null;
}

export function useAuth() {
  const [tokens, setTokens] = useState<AuthTokens>({
    accessToken: null,
    refreshToken: null,
  });

  useEffect(() => {
    // 페이지 로드 시 저장된 토큰 확인
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    setTokens({ accessToken, refreshToken });
  }, []);

  const loginWithGoogle = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/auth/google/auth-url');
      const { auth_url } = await response.json();
      window.location.href = auth_url;
    } catch (error) {
      console.error('구글 로그인 시작 실패:', error);
    }
  };

  const loginWithKakao = async () => {
    try {
      const redirectUri = encodeURIComponent('http://localhost:3000/kakao-callback');
      const response = await fetch(
        `http://localhost:8080/oauth2/kakao/auth-url?redirect_uri=${redirectUri}`
      );
      const { auth_url } = await response.json();
      window.location.href = auth_url;
    } catch (error) {
      console.error('카카오 로그인 시작 실패:', error);
    }
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setTokens({ accessToken: null, refreshToken: null });
  };

  const isAuthenticated = () => {
    return tokens.accessToken !== null;
  };

  return {
    tokens,
    loginWithGoogle,
    loginWithKakao,
    logout,
    isAuthenticated,
  };
}
```

```typescript
// components/LoginButton.tsx
'use client';

import { useAuth } from '@/hooks/useAuth';

export default function LoginButton() {
  const { loginWithGoogle, loginWithKakao } = useAuth();

  return (
    <div className="flex flex-col gap-4">
      <button
        onClick={loginWithGoogle}
        className="px-6 py-3 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
      >
        구글로 로그인
      </button>
      
      <button
        onClick={loginWithKakao}
        className="px-6 py-3 bg-yellow-400 text-black rounded-lg hover:bg-yellow-500"
      >
        카카오로 로그인
      </button>
    </div>
  );
}
```

```typescript
// utils/api.ts
const API_BASE_URL = 'http://localhost:8080';

export async function apiRequest(
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> {
  const token = localStorage.getItem('accessToken');
  
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    // 토큰 만료 처리
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      // Refresh Token으로 새 토큰 발급 시도
      // (위의 refreshAccessToken 함수 사용)
    } else {
      // 로그인 페이지로 리다이렉트
      window.location.href = '/login';
    }
  }

  return response;
}

// 사용 예시
export async function getUserProfile() {
  const response = await apiRequest('/api/users/profile');
  return response.json();
}
```

---

## 주의사항

1. **프로덕션 환경**: Base URL을 환경 변수로 관리하세요.
   ```typescript
   const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
   ```

2. **토큰 보안**: 
   - 프로덕션에서는 `httpOnly` 쿠키 사용을 고려하세요.
   - XSS 공격 방지를 위해 중요한 정보는 서버에서만 처리하세요.

3. **에러 처리**: 
   - 모든 API 호출에 try-catch를 사용하세요.
   - 사용자에게 명확한 에러 메시지를 표시하세요.

4. **CORS**: 
   - 프로덕션 도메인은 백엔드에 등록되어야 합니다.

---

## 추가 리소스

- Swagger UI: `http://localhost:8080/docs`
- API 문서: `http://localhost:8080/v3/api-docs`

---

**문의사항이 있으시면 백엔드 팀에 연락해주세요.**

