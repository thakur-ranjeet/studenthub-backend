package com.example.demo.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // <--- ADDED THIS LINE TO NOT RECOGNIZE THE EXTRA FIELD
public class StudentEventDto {

    private int sid;
    private String sname;
    private int crsId;
    private String smail;

    // Default constructor is REQUIRED by Jackson serializer/deserializer
    public StudentEventDto() {
    }
    
    public StudentEventDto(int sid, String sname, int crsId, String smail) {
        this.sid = sid;
        this.sname = sname;
        this.crsId = crsId;
        this.smail = smail;
    }

    // Getters and Setters
    public int getSid() {
        return sid;
    }

    public void setSid(int sid) {
        this.sid = sid;
    }

    public String getSname() {
        return sname;
    }

    public void setSname(String sname) {
        this.sname = sname;
    }

    public int getCrsId() {
        return crsId;
    }

    public void setCrsId(int crsId) {
        this.crsId = crsId;
    }

    public String getSmail() {
        return smail;
    }

    public void setSmail(String smail) {
        this.smail = smail;
    }

    @Override
    public String toString() {
        return "StudentEventDto [sid=" + sid + ", sname=" + sname + ", crsId=" + crsId + ", smail=" + smail + "]";
    }
}
