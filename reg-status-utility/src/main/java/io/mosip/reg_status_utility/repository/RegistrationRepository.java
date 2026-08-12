package io.mosip.reg_status_utility.repository;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.entity.RegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, String> {

    @Modifying
    @Transactional
    @Query(value = "UPDATE regprc.registration SET status_code = 'PROCESSING' WHERE status_code = 'RESUMABLE' AND reg_stage_name IN ('MVSStage','ManualAdjudicationStage')", nativeQuery = true)
    void updateStatusCodes ();

    @Modifying
    @Transactional
    @Query(value = "UPDATE regprc.registration " +
            "SET status_code = 'RESUMABLE', " +
            "latest_trn_status_code = 'REPROCESS', " +
            "upd_dtimes = '2025-07-20 04:00:04.732181', " +
            "trn_retry_count = 0, " +
            "reg_process_retry_count = 0 " +
            "WHERE reg_stage_name = 'PacketValidatorStage' " +
            "AND process = 'CRVS_NEW' " +
            "AND status_code IN ('PROCESSING', 'REPROCESS')",
            nativeQuery = true)
    int updateOpenCrvs();

    @Query(value = "SELECT r.status_code AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r GROUP BY r.status_code", nativeQuery = true)
    List<StatusCodeCountProjection> getStatusCodeCount ();

    @Query(value = "SELECT COALESCE(r.applicant_type, r.process) AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r GROUP BY COALESCE(r.applicant_type, r.process)", nativeQuery = true)
    List<StatusCodeCountProjection> getProcessTypeCountCumulative ();

    @Query(value = "SELECT COALESCE(r.applicant_type, r.process) AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r WHERE r.cr_dtimes LIKE :datePattern GROUP BY COALESCE(r.applicant_type, r.process)", nativeQuery = true)
    List<StatusCodeCountProjection> getProcessTypeCountByDate(@Param("datePattern") String datePattern);

    @Query(value = "SELECT COALESCE(r.applicant_type, r.process) AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r WHERE r.status_code = 'PROCESSED' GROUP BY COALESCE(r.applicant_type, r.process)", nativeQuery = true)
    List<StatusCodeCountProjection> getProcessedCountCumulative();

    @Query(value = "SELECT COALESCE(r.applicant_type, r.process) AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.registration r WHERE r.status_code = 'PROCESSED' AND r.upd_dtimes LIKE :datePattern GROUP BY COALESCE(r.applicant_type, r.process)", nativeQuery = true)
    List<StatusCodeCountProjection> getProcessedCountByDate(@Param("datePattern") String datePattern);

    @Query(value = "SELECT r.status_code AS statusCode, COUNT(DISTINCT r.reg_id) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime FROM regprc.reg_manual_verification r WHERE r.cr_dtimes LIKE :datePattern AND r.status_code != 'PENDING' GROUP BY r.status_code", nativeQuery = true)
    List<StatusCodeCountProjection> getMAStatsByDate(@Param("datePattern") String datePattern);

    @Query(value = "SELECT 'INQUEUE' AS statusCode, COUNT(DISTINCT r.reg_id) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime " +
            "FROM regprc.reg_manual_verification r WHERE r.status_code = 'INQUEUE'", nativeQuery = true)
    List<StatusCodeCountProjection> getMAStatsTotal();

    @Query(value = "SELECT r.status_code AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime " +
            "FROM regprc.registration r WHERE r.status_code IN ('FAILED','ON_HOLD') AND r.upd_dtimes LIKE :datePattern GROUP BY r.status_code", nativeQuery = true)
    List<StatusCodeCountProjection> getHoldAndFailedByDate(@Param("datePattern") String datePattern);
}
