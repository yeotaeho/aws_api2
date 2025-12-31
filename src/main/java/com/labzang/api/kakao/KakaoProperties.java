package com.labzang.api.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "kakao")
@Getter
@Setter
public class KakaoProperties {
    private String restApiKey;
    private String redirectUri;
    
    private static final String KAKAO_AUTH_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    
    public String getAuthUrl() {
        return KAKAO_AUTH_URL + "?client_id=" + restApiKey 
                + "&redirect_uri=" + redirectUri 
                + "&response_type=code";
    }
    
    public String getTokenUrl() {
        return KAKAO_TOKEN_URL;
    }
    
    public String getUserInfoUrl() {
        return KAKAO_USER_INFO_URL;
    }
}

