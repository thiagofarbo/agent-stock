package com.agent.flight.request;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class FlightRequest implements Serializable {

    private String search;

}
