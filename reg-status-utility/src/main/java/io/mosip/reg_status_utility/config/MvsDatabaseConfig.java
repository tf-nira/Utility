package io.mosip.reg_status_utility.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(
        basePackages = "io.mosip.reg_status_utility.repository.mvs",
        entityManagerFactoryRef = "mvsEntityManagerFactory",
        transactionManagerRef = "mvsTransactionManager"
)
public class MvsDatabaseConfig {

    @Value("${spring.datasource.mvs.url}")
    private String url;

    @Value("${spring.datasource.mvs.username}")
    private String username;

    @Value("${spring.datasource.mvs.password}")
    private String password;

    @Value("${spring.datasource.mvs.driver-class-name}")
    private String driverClassName;

    @Bean
    public DataSource mvsDataSource() {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
        dataSource.setPoolName("mvs-pool");
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setValidationTimeout(5000);
        dataSource.setIdleTimeout(300000);
        dataSource.setMaxLifetime(900000);
        dataSource.setInitializationFailTimeout(1);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean mvsEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mvsDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("io.mosip.reg_status_utility.entity.mvs")
                .persistenceUnit("mvs")
                .properties(jpaProperties())
                .build();
    }

    @Bean
    public PlatformTransactionManager mvsTransactionManager(
            @Qualifier("mvsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    private Map<String, Object> jpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.temp.use_jdbc_metadata_defaults", false);
        props.put("hibernate.jdbc.lob.non_contextual_creation", true);
        return props;
    }
}
