package com.ngolahealth.backend.config;

import com.ngolahealth.backend.modules.audit.application.AuditSafetyNetInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final AuditSafetyNetInterceptor auditSafetyNet;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(auditSafetyNet);
  }
}
