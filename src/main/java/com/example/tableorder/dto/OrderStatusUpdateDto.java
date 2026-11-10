package com.example.tableorder.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OrderStatusUpdateDto {

    @NotBlank
    private String status;  // ORDERED | PREPARING | SERVED | COMPLETED | CANCELLED
}