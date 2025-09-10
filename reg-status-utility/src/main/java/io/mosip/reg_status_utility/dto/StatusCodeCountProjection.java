package io.mosip.reg_status_utility.dto;

import java.sql.Date;
import java.sql.Time;

public interface StatusCodeCountProjection {

    String getStatusCode();
    Long getCount();
    Date getCurrentDate();
    Time getCurrentTime();
}
