package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.Set;

public class ScanRequest {

    private String scanPath;
    private Set<String> acceptedExtensions;

    public String getScanPath() {
        return scanPath;
    }

    public void setScanPath(String scanPath) {
        this.scanPath = scanPath;
    }

    public Set<String> getAcceptedExtensions() {
        return acceptedExtensions;
    }

    public void setAcceptedExtensions(Set<String> acceptedExtensions) {
        this.acceptedExtensions = acceptedExtensions;
    }
}