package io.mosip.packet_utility.service;

import io.mosip.packet_utility.dto.NINResultDTO;
import io.mosip.packet_utility.dto.RegIdIdRepoStatusDTO;
import org.springframework.stereotype.Service;

@Service
public interface PacketService {

    public void getPacketNIN() throws Exception;
    public NINResultDTO getNIN(String rid);
    public void getNINStatus () throws Exception;
    public void updateIdentity () throws  Exception;
    public void getResidenceStatus() throws  Exception;
    public void getEnrolmentStatus() throws Exception;
    public void getAge() throws  Exception;
    public void getPrn() throws  Exception;
    public void extractFaceBiometrics() throws Exception;
    public void addOrUpdateMergedTag() throws Exception;
    public void searchApplicantFields(String inputFile) throws Exception;
    public void searchResidenceFields(String inputFile) throws Exception;
    public void extractDocuments(String inputFile) throws Exception;
    public void getFacilityDetails() throws Exception;
    public void updateResidence () throws Exception;
    public void getApplicantResidence() throws Exception;
    public void getResidenceCorrection() throws Exception;
    public void getApplicantEnrolment() throws Exception;
    public void checkNameInfo() throws Exception;
    public void checkIdRepoByRegIdStatus() throws Exception;
    public RegIdIdRepoStatusDTO checkRegIdInIdRepo(String regId);
}
