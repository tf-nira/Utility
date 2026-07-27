package io.mosip.reg_status_utility.repository.print;

import io.mosip.reg_status_utility.dto.StatusCodeCountProjection;
import io.mosip.reg_status_utility.entity.print.CardDetailEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardDetailRepository extends JpaRepository<CardDetailEntity, String> {

    @Query(value = "SELECT " +
            "'PRINTED' AS statusCode, " +
            "COUNT(*) AS count, " +
            "CURRENT_DATE AS currentDate, " +
            "CURRENT_TIME AS currentTime " +
            "FROM print.card_detail " +
            "WHERE is_pushed = 'true' " +
            "AND upd_dtimes LIKE ?1",
            nativeQuery = true)
    List<StatusCodeCountProjection> getPrintingCountByDate(String datePattern);


    @Query(value = "SELECT " +
            "'PRINTED' AS statusCode, " +
            "COUNT(*) AS count, " +
            "CURRENT_DATE AS currentDate, " +
            "CURRENT_TIME AS currentTime " +
            "FROM print.card_detail " +
            "WHERE is_pushed = 'true'",
            nativeQuery = true)
    List<StatusCodeCountProjection> getPrintingCountTotal();

}