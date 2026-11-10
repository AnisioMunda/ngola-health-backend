package com.ngolahealth.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Relógio injectável (testes controlam o tempo) e tarefas agendadas. */
@Configuration
@EnableScheduling
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
