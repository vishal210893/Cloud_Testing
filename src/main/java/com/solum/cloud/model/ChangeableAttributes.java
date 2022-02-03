package com.solum.cloud.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeableAttributes{
	private boolean readEnabled;
	private boolean listEnabled;
	private boolean deleteEnabled;
	private boolean writeEnabled;
}