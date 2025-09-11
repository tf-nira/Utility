package io.mosip.reg_status_utility.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "io.mosip.reg_status_utility.repository",
        entityManagerFactoryRef = "credentialEntityManagerFactory",
        transactionManagerRef = "credentialTransactionManager"
)
public class CredentialDBConfig {

    @Bean(name = "credentialDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.credential")
    public DataSource credentialDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "credentialEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean credentialEntityManagerFactory(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(credentialDataSource())
                .packages("io.mosip.reg_status_utility.entity")
                .persistenceUnit("credential")
                .build();
    }

    @Bean(name = "credentialTransactionManager")
    public PlatformTransactionManager credentialTransactionManager(EntityManagerFactoryBuilder builder) {
        return new JpaTransactionManager(credentialEntityManagerFactory(builder).getObject());
    }
}