package com.example.tableorder;

import com.example.tableorder.support.MySqlTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 MySQL에서 전체 컨텍스트를 띄운다.
 * Flyway 마이그레이션(V1~), JPQL 쿼리 검증, 빈 구성이 모두 통과해야 성공한다.
 */
@SpringBootTest
@Import(MySqlTestcontainersConfig.class)
class TableOrderApplicationTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void orderStatusesAreNormalizedByMigration() {
        Integer invalid = jdbc.queryForObject("""
                SELECT COUNT(*) FROM `order`
                WHERE status NOT IN ('ORDERED','PREPARING','SERVED','COMPLETED','CANCELLED')
                """, Integer.class);
        assertThat(invalid).isZero();
    }
}
