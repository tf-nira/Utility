package io.mosip.reg_status_utility.repository.ida;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.entity.ida.IdentityCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdaRepository extends JpaRepository<IdentityCacheEntity, String> {

    @Query(value = "SELECT 'STORED' AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM ida.identity_cache", nativeQuery = true)
    List<StatusCodeCountProjection> getIDAStoredCountTotal ();

    @Query(value = "SELECT 'STORED' AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM ida.credential_event_store WHERE cr_dtimes LIKE :datePattern", nativeQuery = true)
    List<StatusCodeCountProjection> getIDAStoredCountByDate(@Param("datePattern") String datePattern);
}