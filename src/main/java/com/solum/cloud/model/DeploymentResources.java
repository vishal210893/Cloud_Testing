package com.solum.cloud.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
public class DeploymentResources {

    private String cpu;

    private String memory;

}
