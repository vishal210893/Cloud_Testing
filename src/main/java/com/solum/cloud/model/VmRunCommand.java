package com.solum.cloud.model;

import lombok.Data;

@Data
public class VmRunCommand {

    private String dbUri;

    private String mongoUri;

    private String mongoDb;

    private String updatePeriod;

}
