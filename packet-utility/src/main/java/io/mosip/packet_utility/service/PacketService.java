package io.mosip.packet_utility.service;

import org.springframework.stereotype.Service;

@Service
public interface PacketService {

    public void getPacketNIN () throws Exception;
    public void getNINStatus () throws Exception;
    public void updateIdentity () throws  Exception;
}
