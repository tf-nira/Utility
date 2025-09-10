package io.mosip.reg_status_utility.repository;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.entity.RegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, String> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE regprc.registration SET status_code = 'PROCESSING' WHERE status_code = 'RESUMABLE' AND reg_stage_name IN ('MVSStage','ManualAdjudicationStage')", nativeQuery = true)
    void updateStatusCodes ();

    @Query(value = "SELECT r.status_code AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r GROUP BY r.status_code", nativeQuery = true)
    List<StatusCodeCountProjection> getStatusCodeCount ();
}
