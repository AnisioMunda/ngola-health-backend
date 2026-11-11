package com.ngolahealth.backend.config;

import com.ngolahealth.backend.security.tenant.HospitalTenantResolver;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Liga o isolamento por hospital (coluna {@code @TenantId}) ao Hibernate. */
@Configuration
public class TenantHibernateConfig {

  @Bean
  HibernatePropertiesCustomizer tenantResolverCustomizer(HospitalTenantResolver resolver) {
    return properties ->
        properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
  }
}
