package com.labzang.api.token.repository;

import com.labzang.api.token.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    /**
     * provider와 userId로 Refresh Token 조회
     */
    Optional<RefreshToken> findByProviderAndUserId(String provider, String userId);
    
    /**
     * provider와 userId로 Refresh Token 삭제
     */
    void deleteByProviderAndUserId(String provider, String userId);
    
    /**
     * 만료된 토큰 삭제
     */
    void deleteByExpiresAtBefore(LocalDateTime now);
}

