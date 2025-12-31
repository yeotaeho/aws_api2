# AWS EC2 CI/CD 배포 전략

## 🚀 핵심 3가지 필수 사항

### 1️⃣ GHCR 사용 (SCP 대신)
- ✅ **배포 속도 3-5배 향상** (SCP 3-5분 → GHCR 30초-1분)
- ✅ GitHub 네이티브 통합 (추가 Secret 불필요)
- ✅ Private 저장소 무료 지원
- 📌 **설정 방법**: [7. GHCR 사용](#7-권장-전략-ghcr-github-container-registry-사용)

### 2️⃣ api.labzang.com 폴더를 Context로 지정
- ✅ GitHub Actions에서 `working-directory: ./api.labzang.com` 명시
- ✅ 빌드 및 Docker 이미지 생성 시 올바른 경로 사용
- 📌 **설정 방법**: [3. GitHub Actions Workflow](#3-github-actions-workflow-ghcr-사용---권장)

### 3️⃣ t3.micro Swap 메모리 설정 (필수!)
- ✅ **메모리 부족 방지** (1GB → 3GB 사용 가능)
- ✅ 2GB Swap 권장
- ✅ 빌드/실행 중 OOM(Out of Memory) 에러 방지
- 📌 **설정 방법**: [5.1 Swap 메모리 설정](#51-swap-메모리-설정-t3micro-필수)

---

## 목차
1. [인프라 구성](#1-인프라-구성)
2. [필요한 AWS 리소스](#2-필요한-aws-리소스)
3. [GitHub Actions Workflow](#3-github-actions-workflow)
4. [GitHub Secrets 설정](#4-github-secrets-설정)
5. [EC2 인스턴스 초기 설정](#5-ec2-인스턴스-초기-설정)
6. [Docker Compose 설정](#6-docker-compose-설정)
7. [대안 전략: Docker Hub 사용](#7-대안-전략-docker-hub-사용)
8. [배포 프로세스 흐름](#8-배포-프로세스-흐름)
9. [롤백 전략](#9-롤백-전략)
10. [모니터링 및 로그](#10-모니터링-및-로그)
11. [Security Group 설정](#11-security-group-설정)
12. [비용 최적화 팁](#12-비용-최적화-팁)

---

## 🎯 빠른 시작 가이드 (5분 완성)

### Step 1: EC2 인스턴스 생성 및 Swap 설정
```bash
# SSH 접속 후
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Step 2: Docker 설치
```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker ubuntu
sudo systemctl enable docker && sudo systemctl start docker
```

### Step 3: GitHub Actions Workflow 생성
`.github/workflows/deploy.yml` 파일 생성 후 [3번 섹션](#3-github-actions-workflow-ghcr-사용---권장) 내용 복사

### Step 4: GitHub Secrets 설정
- `EC2_HOST`: EC2 퍼블릭 IP
- `EC2_USERNAME`: ubuntu
- `EC2_SSH_KEY`: Private Key 전체 내용

### Step 5: GHCR 패키지 Public 설정
GitHub Repository → Packages → Change visibility → Public

### Step 6: Push & 배포!
```bash
git push origin main
# GitHub Actions에서 자동 배포 시작!
```

---

## 1. 인프라 구성

```
GitHub Repository
    ↓
GitHub Actions (CI/CD Pipeline)
    ↓
AWS EC2 Instance (Ubuntu/Amazon Linux)
    ↓
Docker Container (Spring Boot Application)
```

---

## 2. 필요한 AWS 리소스

- **EC2 Instance**: 애플리케이션 실행 서버
- **Security Group**: 포트 8080 (애플리케이션), 22 (SSH) 오픈
- **IAM User**: GitHub Actions용 배포 권한
- **Elastic IP** (선택): 고정 IP 주소

---

## 3. GitHub Actions Workflow (GHCR 사용 - 권장)

`.github/workflows/deploy.yml` 파일 생성:

```yaml
name: Deploy to AWS EC2 via GHCR

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    # 1. 코드 체크아웃
    - name: Checkout code
      uses: actions/checkout@v3
    
    # 2. JDK 설정
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    # 3. Gradle 빌드 (api.labzang.com 폴더를 context로 지정)
    - name: Build with Gradle
      working-directory: ./api.labzang.com
      run: ./gradlew clean build
    
    # 4. GHCR 로그인
    - name: Login to GitHub Container Registry
      uses: docker/login-action@v2
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    # 5. Docker 이미지 빌드 및 푸시
    - name: Build and Push Docker image to GHCR
      working-directory: ./api.labzang.com
      run: |
        IMAGE_NAME=ghcr.io/${{ github.repository_owner }}/api-app:latest
        docker build -t $IMAGE_NAME .
        docker push $IMAGE_NAME
    
    # 6. EC2에서 배포 스크립트 실행
    - name: Deploy to EC2
      uses: appleboy/ssh-action@master
      with:
        host: ${{ secrets.EC2_HOST }}
        username: ${{ secrets.EC2_USERNAME }}
        key: ${{ secrets.EC2_SSH_KEY }}
        script: |
          cd /home/ubuntu/app
          
          # GHCR 로그인 (Public 패키지는 불필요)
          echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          
          # 이미지 Pull 및 배포
          docker pull ghcr.io/${{ github.repository_owner }}/api-app:latest
          docker-compose down
          docker-compose up -d
          docker image prune -f
```

**주요 변경사항:**
- ✅ SCP 대신 GHCR 사용으로 배포 속도 3-5배 향상
- ✅ `working-directory: ./api.labzang.com` 추가로 경로 명시
- ✅ `GITHUB_TOKEN` 자동 사용 (추가 Secret 불필요)

---

## 4. GitHub Secrets 설정

Repository Settings → Secrets and variables → Actions에서 다음 항목 설정:

| Secret Name | 설명 | 예시 |
|------------|------|------|
| `EC2_HOST` | EC2 인스턴스의 퍼블릭 IP 또는 도메인 | `13.125.123.45` |
| `EC2_USERNAME` | EC2 SSH 사용자명 | `ubuntu` 또는 `ec2-user` |
| `EC2_SSH_KEY` | EC2 접속용 Private Key 전체 내용 | `-----BEGIN RSA PRIVATE KEY-----...` |

### GitHub Secrets 추가 방법
1. GitHub Repository → Settings
2. Secrets and variables → Actions
3. New repository secret 클릭
4. Name과 Secret 입력 후 Add secret

---

## 5. EC2 인스턴스 초기 설정

EC2 인스턴스에 SSH 접속 후 실행:

### 5.1 Swap 메모리 설정 (t3.micro 필수!)

**t3.micro는 메모리가 1GB밖에 없어 빌드/실행 시 메모리 부족 발생 가능**

```bash
# Swap 메모리 생성 (2GB 권장)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 영구 적용 (재부팅 후에도 유지)
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Swap 확인
free -h
# 출력 예시:
#               total        used        free      shared  buff/cache   available
# Mem:           990Mi       450Mi       200Mi       1.0Mi       340Mi       400Mi
# Swap:          2.0Gi       100Mi       1.9Gi

# Swap 사용률 조정 (선택, 기본값 60)
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
```

### 5.2 Docker 설치

```bash
# Docker 설치 (Ubuntu)
sudo apt-get update
sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker ubuntu
sudo systemctl enable docker
sudo systemctl start docker

# Docker 설치 (Amazon Linux 2)
sudo yum update -y
sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker ec2-user
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 5.3 애플리케이션 디렉토리 및 환경 변수 설정

```bash
# 애플리케이션 디렉토리 생성
mkdir -p /home/ubuntu/app
cd /home/ubuntu/app

# 환경 변수 파일 생성
cat > .env << EOF
KAKAO_CLIENT_ID=your_kakao_client_id
KAKAO_CLIENT_SECRET=your_kakao_client_secret
KAKAO_REDIRECT_URI=your_redirect_uri
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
GOOGLE_REDIRECT_URI=your_redirect_uri
REDIS_HOST=your_redis_host
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password
EOF

# 재접속 (docker 그룹 적용)
exit
# SSH 재접속

# Docker 설치 확인
docker --version
docker-compose --version
```

### 5.4 시스템 리소스 모니터링 설정

```bash
# htop 설치 (선택, 리소스 모니터링 도구)
sudo apt-get install -y htop

# 메모리 사용량 실시간 모니터링
watch -n 1 free -h
```

---

## 6. Docker Compose 설정

`docker-compose.yml` 파일 (EC2용, GHCR 이미지 사용):

```yaml
version: '3.8'

services:
  api:
    image: ghcr.io/yourusername/api-app:latest
    container_name: api-service
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=railway
      - KAKAO_CLIENT_ID=${KAKAO_CLIENT_ID}
      - KAKAO_CLIENT_SECRET=${KAKAO_CLIENT_SECRET}
      - KAKAO_REDIRECT_URI=${KAKAO_REDIRECT_URI}
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
      - GOOGLE_REDIRECT_URI=${GOOGLE_REDIRECT_URI}
      - REDIS_HOST=${REDIS_HOST}
      - REDIS_PORT=${REDIS_PORT}
      - REDIS_PASSWORD=${REDIS_PASSWORD}
    env_file:
      - .env
    restart: always
    # t3.micro 메모리 제한 설정 (선택)
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
    networks:
      - app-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

networks:
  app-network:
    driver: bridge
```

**주요 설정:**
- ✅ GHCR 이미지 경로 사용 (`ghcr.io/yourusername/api-app:latest`)
- ✅ 메모리 제한 설정으로 OOM(Out of Memory) 방지
- ✅ 헬스체크로 컨테이너 상태 자동 모니터링

---

## 7. 권장 전략: GHCR (GitHub Container Registry) 사용

**GHCR을 사용하면 Docker Hub보다 빠르고 GitHub와 완벽하게 통합됩니다.**

### 7.1 GHCR 장점

- ✅ GitHub 계정으로 바로 사용 가능 (별도 가입 불필요)
- ✅ Private 저장소 무료 제공
- ✅ GitHub Actions와 네이티브 통합
- ✅ 빠른 이미지 전송 속도
- ✅ SCP 방식보다 3-5배 빠름

### 7.2 GitHub Actions Workflow (GHCR 버전)

```yaml
name: Deploy to AWS EC2 via GHCR

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    # api.labzang.com 폴더를 context로 지정
    - name: Build with Gradle
      working-directory: ./api.labzang.com
      run: ./gradlew clean build
    
    - name: Login to GitHub Container Registry
      uses: docker/login-action@v2
      with:
        registry: ghcr.io
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Build and Push Docker image
      working-directory: ./api.labzang.com
      run: |
        IMAGE_NAME=ghcr.io/${{ github.repository_owner }}/api-app:latest
        docker build -t $IMAGE_NAME .
        docker push $IMAGE_NAME
    
    - name: Deploy to EC2
      uses: appleboy/ssh-action@master
      with:
        host: ${{ secrets.EC2_HOST }}
        username: ${{ secrets.EC2_USERNAME }}
        key: ${{ secrets.EC2_SSH_KEY }}
        script: |
          cd /home/ubuntu/app
          
          # GHCR 로그인
          echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          
          # 이미지 Pull 및 배포
          docker pull ghcr.io/${{ github.repository_owner }}/api-app:latest
          docker-compose down
          docker-compose up -d
          docker image prune -f
```

### 7.3 GHCR 설정

**추가 Secret 불필요!** GitHub Actions의 기본 `GITHUB_TOKEN`을 자동으로 사용합니다.

단, GHCR 패키지를 Public으로 만들거나 EC2에서 접근 권한 설정이 필요합니다:

#### 방법 1: GHCR 패키지를 Public으로 설정
1. GitHub Repository → Packages
2. 해당 패키지 선택
3. Package settings → Change visibility → Public

#### 방법 2: Personal Access Token 사용 (Private 유지)
1. GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token (classic)
3. 권한: `read:packages`, `write:packages`
4. GitHub Secrets에 `GHCR_TOKEN` 추가

### 7.4 docker-compose.yml (GHCR 버전)

```yaml
version: '3.8'

services:
  api:
    image: ghcr.io/yourusername/api-app:latest
    container_name: api-service
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=railway
    env_file:
      - .env
    restart: always
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

### 7.5 GHCR vs SCP vs Docker Hub 비교

| 항목 | GHCR | Docker Hub | SCP |
|------|------|------------|-----|
| 전송 속도 | ⚡⚡⚡ 매우 빠름 | ⚡⚡ 빠름 | ⚡ 느림 |
| 설정 난이도 | ✅ 쉬움 | ✅ 쉬움 | ⚠️ 복잡 |
| 추가 비용 | 무료 | 무료 (제한) | 무료 |
| GitHub 통합 | ✅ 완벽 | ❌ 별도 | ❌ 별도 |
| Private 지원 | ✅ 무료 | ⚠️ 유료 | ✅ 무료 |
| **권장도** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |

---

## 8. 배포 프로세스 흐름

```
1. 개발자가 main 브랜치에 push
   ↓
2. GitHub Actions 트리거
   ├─ 코드 체크아웃
   ├─ JDK 설정
   ├─ Gradle 빌드 및 테스트
   └─ Docker 이미지 빌드
   ↓
3. 이미지 전송
   ├─ 방법 A: Docker 이미지를 tar로 저장 → SCP로 EC2에 전송
   └─ 방법 B: Docker Hub에 푸시
   ↓
4. EC2에서 배포
   ├─ Docker 이미지 로드/Pull
   ├─ 기존 컨테이너 중지 (docker-compose down)
   ├─ 새 컨테이너 시작 (docker-compose up -d)
   └─ 구 이미지 정리 (docker image prune)
   ↓
5. 배포 완료 및 헬스체크
```

### 배포 시간 예상
- 빌드: 2-3분
- 이미지 전송: **30초-1분 (GHCR)** / 1-2분 (Docker Hub) / 3-5분 (SCP)
- 배포: 30초-1분
- **총 소요 시간: 약 3-5분 (GHCR 사용 시)**

**GHCR 사용 시 SCP 대비 2-3분 단축!**

---

## 9. 롤백 전략

### 9.1 자동 백업

배포 스크립트에 백업 추가:

```bash
# EC2 배포 스크립트에 추가
cd /home/ubuntu/app

# 현재 이미지 백업
docker tag api-app:latest api-app:backup-$(date +%Y%m%d-%H%M%S)

# 새 이미지 로드
docker load < api-app.tar.gz

# 배포
docker-compose down
docker-compose up -d
```

### 9.2 수동 롤백

```bash
# 백업 이미지 목록 확인
docker images | grep api-app

# 특정 백업 버전으로 롤백
docker stop api-service
docker rm api-service
docker run -d --name api-service \
  --env-file .env \
  -p 8080:8080 \
  api-app:backup-20250101-120000

# 또는 docker-compose.yml 수정 후
docker-compose up -d
```

### 9.3 Git 태그를 활용한 버전 관리

```yaml
# GitHub Actions에서 태그 기반 배포
- name: Build Docker image with tag
  run: |
    docker build -t api-app:${{ github.sha }} .
    docker tag api-app:${{ github.sha }} api-app:latest
```

---

## 10. 모니터링 및 로그

### 10.1 컨테이너 상태 확인

```bash
# 실행 중인 컨테이너 확인
docker ps

# 모든 컨테이너 확인 (중지된 것 포함)
docker ps -a

# 컨테이너 리소스 사용량
docker stats api-service
```

### 10.2 로그 확인

```bash
# 실시간 로그 확인
docker logs -f api-service

# 최근 100줄 로그
docker logs --tail 100 api-service

# 특정 시간 이후 로그
docker logs --since 2024-01-01T00:00:00 api-service

# 로그 파일로 저장
docker logs api-service > /home/ubuntu/logs/app-$(date +%Y%m%d).log
```

### 10.3 헬스체크

```bash
# 애플리케이션 헬스체크
curl http://localhost:8080/actuator/health

# 상세 헬스체크
curl http://localhost:8080/actuator/health | jq

# 외부에서 접근
curl http://YOUR_EC2_PUBLIC_IP:8080/actuator/health
```

### 10.4 CloudWatch 연동 (선택)

```bash
# CloudWatch Logs Agent 설치
sudo yum install amazon-cloudwatch-agent

# Docker 로그를 CloudWatch로 전송
docker run -d \
  --log-driver=awslogs \
  --log-opt awslogs-region=ap-northeast-2 \
  --log-opt awslogs-group=/aws/ec2/api-service \
  api-app:latest
```

---

## 11. Security Group 설정

### 11.1 Inbound Rules

| Type | Protocol | Port Range | Source | Description |
|------|----------|------------|--------|-------------|
| SSH | TCP | 22 | My IP | SSH 접속 (보안을 위해 특정 IP만 허용) |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 | 애플리케이션 포트 |
| HTTPS | TCP | 443 | 0.0.0.0/0 | SSL 사용 시 (선택) |
| HTTP | TCP | 80 | 0.0.0.0/0 | HTTP 리다이렉트용 (선택) |

### 11.2 Outbound Rules

| Type | Protocol | Port Range | Destination | Description |
|------|----------|------------|-------------|-------------|
| All traffic | All | All | 0.0.0.0/0 | 모든 아웃바운드 허용 |

### 11.3 보안 강화 팁

```bash
# SSH 포트 변경 (선택)
sudo vi /etc/ssh/sshd_config
# Port 22 → Port 2222 변경
sudo systemctl restart sshd

# Fail2ban 설치 (무차별 대입 공격 방지)
sudo apt-get install fail2ban
sudo systemctl enable fail2ban
sudo systemctl start fail2ban

# UFW 방화벽 설정 (선택)
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp
sudo ufw enable
```

---

## 12. 비용 최적화 팁

### 12.1 인스턴스 타입 선택

| 인스턴스 타입 | vCPU | 메모리 | 가격 (월, 서울 리전) | 권장 용도 | Swap 필요 |
|--------------|------|--------|---------------------|-----------|-----------|
| t3.micro | 2 | 1GB | ~$7.5 | 개발/테스트 | ✅ **필수** |
| t3.small | 2 | 2GB | ~$15 | 소규모 프로덕션 | ⚠️ 권장 |
| t3.medium | 2 | 4GB | ~$30 | 중규모 프로덕션 | ❌ 불필요 |
| t3.large | 2 | 8GB | ~$60 | 대규모 프로덕션 | ❌ 불필요 |

**프리티어**: t2.micro (1년간 월 750시간 무료)

**⚠️ 중요: t3.micro 사용 시 반드시 Swap 메모리 설정!**
- Swap 미설정 시 빌드/실행 중 메모리 부족으로 프로세스 강제 종료 가능
- 2GB Swap 권장 (총 사용 가능 메모리: 1GB + 2GB = 3GB)

### 12.2 비용 절감 전략

1. **Elastic IP 관리**
   - 인스턴스가 실행 중일 때만 무료
   - 미사용 시 과금되므로 불필요하면 해제

2. **스냅샷 관리**
   - 정기적으로 오래된 스냅샷 삭제
   - Lifecycle Manager로 자동화

3. **예약 인스턴스**
   - 1년 약정 시 최대 40% 할인
   - 3년 약정 시 최대 60% 할인

4. **Spot 인스턴스** (개발 환경)
   - 온디맨드 대비 최대 90% 저렴
   - 중단 가능성 있음

5. **CloudWatch 알람 설정**
   ```bash
   # 비정상 트래픽 감지
   # CPU 사용률 80% 이상 시 알람
   # 네트워크 아웃 1GB 초과 시 알람
   ```

6. **Auto Scaling 대신 단일 인스턴스**
   - 초기에는 단일 인스턴스로 시작
   - 트래픽 증가 시 수직 확장 (인스턴스 타입 업그레이드)

### 12.3 모니터링 비용

```bash
# AWS Cost Explorer에서 비용 추적
# 월별 예상 비용:
# - EC2 t3.small: $15
# - Elastic IP: $0 (인스턴스 실행 중)
# - EBS 20GB: $2
# - 데이터 전송: $1-5
# 총 예상: $18-22/월
```

---

## 추가 참고 자료

### GitHub Actions 공식 문서
- https://docs.github.com/en/actions

### Docker 공식 문서
- https://docs.docker.com/

### AWS EC2 문서
- https://docs.aws.amazon.com/ec2/

### 유용한 GitHub Actions
- `appleboy/ssh-action`: SSH 명령 실행
- `appleboy/scp-action`: 파일 전송
- `docker/login-action`: Docker Hub 로그인
- `actions/cache`: 빌드 캐시

---

## 문제 해결 (Troubleshooting)

### 1. GitHub Actions에서 SSH 연결 실패
```bash
# EC2 Security Group에서 22번 포트 확인
# SSH 키 형식 확인 (PEM 형식이어야 함)
# EC2_SSH_KEY에 전체 키 내용 포함 확인
```

### 2. GHCR 이미지 Pull 실패
```bash
# EC2에서 GHCR 로그인 확인
docker login ghcr.io

# 패키지 권한 확인 (GitHub Repository → Packages)
# Public으로 설정하거나 Personal Access Token 사용

# 수동으로 이미지 Pull 테스트
docker pull ghcr.io/yourusername/api-app:latest
```

### 3. 메모리 부족 오류 (t3.micro)
```bash
# Swap 메모리 확인
free -h

# Swap이 없으면 생성
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile

# 컨테이너 메모리 제한 확인
docker stats api-service

# OOM Killer 로그 확인
dmesg | grep -i "out of memory"
```

### 4. Docker 이미지 로드 실패
```bash
# EC2에서 디스크 공간 확인
df -h

# 불필요한 이미지 삭제
docker system prune -a

# 디스크 사용량 확인
du -sh /var/lib/docker
```

### 5. 컨테이너 시작 실패
```bash
# 로그 확인
docker logs api-service

# 환경 변수 확인
docker exec api-service env

# 포트 충돌 확인
sudo netstat -tulpn | grep 8080

# 컨테이너 재시작
docker-compose restart
```

### 6. 빌드 실패
```bash
# Gradle 캐시 삭제
./gradlew clean

# 의존성 다시 다운로드
./gradlew build --refresh-dependencies

# working-directory 경로 확인
# GitHub Actions에서 api.labzang.com 폴더 존재 여부 확인
```

### 7. Swap 메모리 관련 문제
```bash
# Swap 사용 중인지 확인
swapon --show

# Swap 사용률 확인
free -h

# Swap 파일 권한 확인
ls -lh /swapfile
# 출력: -rw------- 1 root root 2.0G (600 권한이어야 함)

# Swap 재활성화
sudo swapoff /swapfile
sudo swapon /swapfile
```

---

이 문서를 참고하여 AWS EC2에 안정적인 CI/CD 파이프라인을 구축하실 수 있습니다.

