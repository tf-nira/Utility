package io.mosip.reg_status_utility.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration", schema = "regprc")
@Data
public class RegistrationEntity {

    @Id
    @Column(name = "reg_id")
    private String regId;

    /** The registration type. */
    @Column(name="process")
    private String registrationType;

    @Column
    private Integer iteration=1;

    /** The reference registration id. */
    @Column(name = "ref_reg_id")
    private String referenceRegistrationId;

    @Column(name = "source")
    private String source;

    /** The status code. */
    @Column(name = "status_code")
    private String statusCode;

    /** The lang code. */
    @Column(name = "lang_code")
    private String langCode;

    /** The status comment. */
    @Column(name = "status_comment")
    private String statusComment;

    /** The latest registration transaction id. */
    @Column(name = "latest_trn_id")
    private String latestRegistrationTransactionId;

    /** The is active. */
    @Column(name = "is_active")
    private Boolean isActive;

    /** The created by. */
    @Column(name = "cr_by")
    private String createdBy;

    /** The create date time. */
    @Column(name = "cr_dtimes", updatable = false)
    private LocalDateTime createDateTime;

    /** packet created date and time */
    @Column(name = "pkt_cr_dtimes")
    private LocalDateTime packetCreatedDateTime;

    /** The updated by. */
    @Column(name = "upd_by")
    private String updatedBy;

    /** The update date time. */
    @Column(name = "upd_dtimes")
    private LocalDateTime updateDateTime;

    /** The is deleted. */
    @Column(name = "is_deleted")
    private Boolean isDeleted;

    /** The deleted date time. */
    @Column(name = "del_dtimes")
    private LocalDateTime deletedDateTime;

    /** The retry count. */
    @Column(name = "trn_retry_count")
    private Integer retryCount;

    /** The applicant type. */
    @Column(name = "applicant_type")
    private String applicantType;

    /** The latest transaction type code. */
    @Column(name = "latest_trn_type_code")
    private String latestTransactionTypeCode;

    /** The latest transaction status code. */
    @Column(name = "latest_trn_status_code")
    private String latestTransactionStatusCode;

    /** The latest transaction times. */
    @Column(name = "latest_trn_dtimes")
    private LocalDateTime latestTransactionTimes;

    /** The registration stage name. */
    @Column(name = "reg_stage_name")
    private String registrationStageName;

    /** The reg process retry count. */
    @Column(name = "reg_process_retry_count")
    private Integer regProcessRetryCount;

    /** The resume time stamp. */
    @Column(name = "resume_timestamp")
    private LocalDateTime resumeTimeStamp;

    /** The default resume action. */
    @Column(name = "default_resume_action")
    private String defaultResumeAction;


    /** The pause rule ids. */
    @Column(name = "pause_rule_ids")
    private String pauseRuleIds;

    /** The last success stage name. */
    @Column(name = "last_success_stage_name")
    private String lastSuccessStageName;

    @Column(name = "needs_notification")
    private Boolean needsNotification;

    @Column(name = "notification_sent")
    private Boolean notificationSent;

    @Column(name = "is_anonymous_profile_added")
    private Boolean isAnonymousProfileAdded;
}
