package com.agent.flight.controller;

import com.agent.flight.request.StockRequest;
import com.agent.flight.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    @GetMapping("/search")
    public ResponseEntity<String> searchStocks(@ModelAttribute StockRequest stockRequest){
        return ResponseEntity.ok(stockService.search(stockRequest.getSearch()));
    }
}
