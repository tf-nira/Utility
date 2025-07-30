package io.mosip.packet_utility.service.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.packet_utility.dto.ObjectStoreDto;
import io.mosip.packet_utility.service.ObjectStore;
import org.apache.commons.io.IOUtils;


@Component
public class ObjectStoreImpl  implements ObjectStore{
	

	/** The object store adapter. */
	@Autowired
	ObjectStoreAdapter objectStoreAdapter;


	/** The Constant DEFAULT_KEY_LENGTH. */
	private static final int DEFAULT_KEY_LENGTH = 8;

	@Override
	public String writeData(String policyId, String subscriberId, MultipartFile file) throws Exception {
		String randomShareKey=null;
		if (file != null && !file.isEmpty()) {
		
			try {
				byte[] fileData = file.getBytes();
				
				randomShareKey = storefile(new ByteArrayInputStream(fileData), policyId, subscriberId);
				
			} catch (Exception e) {
				System.out.println(
						"exception while storing"+e.getMessage());
			}

		}else {
			System.out.println(
					"file is empty");
		}

		return randomShareKey;	
		
	}

	@Override
	public ObjectStoreDto  readData(String policyId,
			String subscriberId,String randomShareKey) throws Exception {
		ObjectStoreDto objectStoreDto = new ObjectStoreDto();
		InputStream inputStream = objectStoreAdapter.getObject(subscriberId, policyId, null, null,
				randomShareKey);
		byte[] dataBytes = IOUtils.toByteArray(inputStream);
		objectStoreDto.setFileBytes(dataBytes);
		return objectStoreDto;
	}
	private String storefile( InputStream filedata, String policyId,
			String subscriberId) {
		int length = DEFAULT_KEY_LENGTH;
	
		String randomShareKey = subscriberId + policyId
				+ DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
				+ generateShortRandomShareKey(length);
		boolean isDataStored = objectStoreAdapter.putObject(subscriberId, policyId, null, null, randomShareKey,
				filedata);
		if(isDataStored) {
			
		}
		System.out.println(
				"Is data stored to object store"+isDataStored);

		return randomShareKey;

	}
	
	private String generateShortRandomShareKey(int byteLength) {
		SecureRandom secureRandom = new SecureRandom();
		byte[] token = new byte[byteLength];
		secureRandom.nextBytes(token);
		return CryptoUtil.encodeToURLSafeBase64(token);
	}
}
