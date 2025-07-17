package io.mosip.packet_utility.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

@Service
public interface PacketService {

    public void getPacketNIN () throws Exception;
    public void getNINStatus () throws Exception;
    public void updateIdentity () throws  Exception;
    public void getDetailsFromIdRepo(String rid);
    public void getDetailsFromPacketManager(String rid);
    public void comparePacketsFromPacketMgrAndIdRepo() throws IOException;
    
}
