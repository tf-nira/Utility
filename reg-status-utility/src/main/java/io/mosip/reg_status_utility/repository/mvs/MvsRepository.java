package io.mosip.reg_status_utility.repository.mvs;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.entity.mvs.MvsApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MvsRepository extends JpaRepository<MvsApplicationEntity, String> {

    @Query(value = "SELECT 'INQUEUE' AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime " +
            "FROM mvs.mvs_application WHERE stage NOT IN ('APPROVED','REJECTED')", nativeQuery = true)
    List<StatusCodeCountProjection> getInqueueCountTotal();

    @Query(value = "SELECT 'INQUEUE' AS statusCode, COUNT(*) AS count, CURRENT_DATE AS currentDate, CURRENT_TIME AS currentTime " +
            "FROM mvs.mvs_application WHERE stage NOT IN ('APPROVED','REJECTED') AND cr_dtimes LIKE :datePattern", nativeQuery = true)
    List<StatusCodeCountProjection> getInqueueCountByDate(String datePattern);
}
