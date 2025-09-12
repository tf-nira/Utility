package io.mosip.reg_status_utility.repository.credential;

import io.mosip.reg_status_utility.dto.CredentialProjection;
import io.mosip.reg_status_utility.entity.credential.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, String> {
    @Query(value = "SELECT credential_id AS credentialId, status_code AS statusCode " +
            "FROM credential.credential_transaction",
            nativeQuery = true)
    List<CredentialProjection> findAllCredentialIdAndStatusCode();
}