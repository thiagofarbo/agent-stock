package com.agent.flight.controller;

import com.agent.flight.request.FlightRequest;
import com.agent.flight.service.FlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;

    @GetMapping("/search")
    public ResponseEntity<String> searchFlights(@ModelAttribute FlightRequest flightRequest){
        return ResponseEntity.ok(flightService.search(flightRequest.getSearch()));
    }
}
