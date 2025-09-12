package io.mosip.reg_status_utility.entity.credential;

import lombok.Data;
import javax.persistence.*;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "credential_transaction", schema = "credential")
@Data
public class CredentialEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "credential_id")
    private String credentialId;

    @Column(name = "request")
    private String request;

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "datashareurl")
    private String dataShareUrl;

    @Column(name = "issuancedata")
    private String issuanceData;

    @Column(name = "signature")
    private String signature;

    @Column(name = "trn_retry_count")
    private Integer retryCount;

    @Column(name = "cr_by")
    private String createdBy;

    @Column(name = "cr_dtimes")
    private LocalDateTime createDateTime;

    @Column(name = "upd_by")
    private String updatedBy;

    @Column(name = "upd_dtimes")
    private LocalDateTime updateDateTime;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "del_dtimes")
    private LocalDateTime deletedDateTime;

    @Column(name = "status_comment")
    private String statusComment;
}