package com.agent.flight.response;

import com.agent.flight.domain.Meta;
import com.agent.flight.domain.Stock;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockResponse implements Serializable {

    @NotNull
    @Valid
    private Meta meta;

    @NotEmpty
    private List<@NotNull @Valid Stock> data;
}
