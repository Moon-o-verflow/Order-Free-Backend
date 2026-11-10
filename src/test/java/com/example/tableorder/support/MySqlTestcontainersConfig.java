package com.example.tableorder.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 MySQL 8 컨테이너. 운영과 같은 엔진에서 Flyway 마이그레이션, JPQL, 행 락 동작을 확인한다.
 * {@code @Import(MySqlTestcontainersConfig.class)} 로 사용한다. 같은 설정을 쓰는 테스트끼리는
 * 스프링 컨텍스트 캐시 덕분에 컨테이너 하나를 공유한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("tableorder")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                        "--default-time-zone=+09:00");
    }
}
