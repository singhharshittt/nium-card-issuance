package com.nium.virtualcard.config;

import org.jooq.conf.RenderNameCase;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * jOOQ configuration to fix H2 identifier quoting issues and ensure unquoted identifier rendering.
 */
@Configuration
public class JooqConfig {

  @Bean
  public Settings jooqSettings() {
    return new Settings()
            .withRenderQuotedNames(RenderQuotedNames.NEVER)
            .withRenderNameCase(RenderNameCase.AS_IS);
  }
}