package com.acme.payments.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineIT {
    @Test
    void freshDatabaseUsesProductionBaselineAndValidates() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine")) {
            pg.start();
            Flyway flyway = Flyway.configure()
                    .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            flyway.validate();
            try (var connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 var statement = connection.createStatement()) {
                try (var rs = statement.executeQuery("select version, type from flyway_schema_history where success = true")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("version")).isEqualTo("3");
                    assertThat(rs.getString("type")).containsIgnoringCase("BASELINE");
                    assertThat(rs.next()).isFalse();
                }
                try (var rs = statement.executeQuery("select numeric_scale from information_schema.columns where table_name='ledger_entries' and column_name='amount'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }
}
