package io.mosip.reg_status_utility.repository;

import io.mosip.reg_status_utility.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, String> {
    // Custom queries for the credential_transaction table can be added here
}