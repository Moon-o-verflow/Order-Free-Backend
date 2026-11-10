package com.example.tableorder.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 MySQL 8 컨테이너. 운영과 같은 엔진에서 Flyway 마이그레이션, JPQL, 행 락 동작을 확인한다.
 * {@code @Import(MySqlTestcontainersConfig.class)} 로 사용한다.
 *
 * 테스트 클래스마다 스프링 컨텍스트 구성이 달라도(@SpringBootTest, @DataJpaTest 등)
 * 컨테이너는 JVM 전체에서 하나만 띄워 공유한다. 컨테이너는 JVM 종료 시 Testcontainers가 정리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfig {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("tableorder")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+09:00");

    static {
        MYSQL.start();
    }

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return MYSQL;
    }
}
