package com.agent.flight.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class FlightResponse implements Serializable {

    private String search;

}
