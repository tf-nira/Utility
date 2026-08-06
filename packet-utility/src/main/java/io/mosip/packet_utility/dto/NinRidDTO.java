package io.mosip.packet_utility.dto;

public class NinRidDTO {
    private String nin;
    private String rid;

    public NinRidDTO() {}

    public NinRidDTO(String nin, String rid) {
        this.nin = nin;
        this.rid = rid;
    }

    public String getNin() { return nin; }
    public void setNin(String nin) { this.nin = nin; }

    public String getRid() { return rid; }
    public void setRid(String rid) { this.rid = rid; }
}
