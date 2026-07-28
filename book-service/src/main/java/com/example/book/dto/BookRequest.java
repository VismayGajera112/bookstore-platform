package com.example.book.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BookRequest(

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @NotNull(message = "authorId is required")
        Long authorId,

        @Pattern(regexp = "\\d{10}|\\d{13}", message = "isbn must be 10 or 13 digits")
        String isbn,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "stock is required")
        @Min(value = 0, message = "stock cannot be negative")
        Integer stock,

        @Size(max = 512, message = "coverUrl must be at most 512 characters")
        String coverUrl
) {
}
