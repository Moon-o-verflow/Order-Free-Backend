package com.example.tableorder.config;

import com.example.tableorder.service.payment.pg.TossProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentConfig {
}
