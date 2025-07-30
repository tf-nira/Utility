package io.mosip.packet_utility.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.mosip.packet_utility.dto.ObjectStoreDto;

@Service
public interface ObjectStore {

	
	   public String writeData(String policyId, String subscriberId, MultipartFile file) throws Exception;
	   public ObjectStoreDto readData(String policyId,
				String subscriberId,String randomShareKey) throws Exception;
}
