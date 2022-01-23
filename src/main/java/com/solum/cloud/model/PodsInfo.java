package com.solum.cloud.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PodsInfo {

    private String instanceId;

    private LocalDateTime creationTime;

    private String status;

    private String cpu;

    private String memory;

    private int restart;

    private String nodeName;

}
