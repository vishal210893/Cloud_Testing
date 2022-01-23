package com.solum.cloud.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class PodsInfo {

    String instanceId;

    LocalDateTime creationTime;

    String status;

    String cpu;

    String memory;

}
