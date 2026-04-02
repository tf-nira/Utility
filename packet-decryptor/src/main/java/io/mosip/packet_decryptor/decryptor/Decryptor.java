package io.mosip.packet_decryptor.decryptor;

import io.mosip.packet_decryptor.exception.ApisResourceAccessException;
import io.mosip.packet_decryptor.exception.PacketDecryptionFailureException;

import java.io.InputStream;

public interface Decryptor {
    /**
     * This Method provide the functionality to decrypt packet
     *
     * @param input
     *            encrypted packet to be decrypted
     * @return decrypted packet
     *
     * @throws PacketDecryptionFailureException
     *             if error occured while decrypting
     * @throws ApisResourceAccessException
     *             if error occured while
     * @throws DateTimeParseException
     *             if fail to parse date from registration id
     */
    public InputStream decrypt(String id, String refId, InputStream input)
            throws PacketDecryptionFailureException, ApisResourceAccessException;
}
