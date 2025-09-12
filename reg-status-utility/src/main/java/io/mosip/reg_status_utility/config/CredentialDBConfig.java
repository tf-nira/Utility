package io.mosip.reg_status_utility.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "io.mosip.reg_status_utility.repository.credential",   // repos for credential DB
        entityManagerFactoryRef = "credentialEntityManagerFactory",
        transactionManagerRef = "credentialTransactionManager"
)
public class CredentialDBConfig {

    @Bean(name = "credentialDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.credential")
    public DataSourceProperties credentialDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "credentialDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.credential")
    public DataSource credentialDataSource(
            @Qualifier("credentialDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "credentialEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean credentialEntityManagerFactory(
            @Qualifier("credentialDataSource") DataSource dataSource,
            EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(dataSource)
                .packages("io.mosip.reg_status_utility.entity.credential") // entities package
                .persistenceUnit("credential")
                .build();
    }

    @Bean(name = "credentialTransactionManager")
    public PlatformTransactionManager credentialTransactionManager(
            @Qualifier("credentialEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
