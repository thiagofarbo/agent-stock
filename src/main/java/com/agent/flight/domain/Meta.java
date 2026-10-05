package com.agent.flight.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Meta implements Serializable {

    @NotNull
    @PositiveOrZero
    private Integer requested;

    @NotNull
    @PositiveOrZero
    private Integer returned;
}
