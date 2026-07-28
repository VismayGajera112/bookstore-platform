package com.example.book.controller;

import com.example.book.dto.CoverMetadataResponse;
import com.example.book.dto.CoverUploadResponse;
import com.example.book.service.CoverService;
import com.example.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{id}/cover")
public class CoverController {

    private final CoverService coverService;

    public CoverController(CoverService coverService) {
        this.coverService = coverService;
    }

    /**
     * ADMIN — returns a short-lived S3 PUT URL. The client uploads the image bytes directly to S3;
     * cover-processor Lambda then writes DynamoDB metadata and sends the confirmation email.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CoverUploadResponse requestUpload(@PathVariable Long id,
                                             @RequestParam(required = false, defaultValue = "image/jpeg")
                                             String contentType) {
        return coverService.createUploadUrl(id, contentType);
    }

    /** PUBLIC — cover URL plus processed metadata once the Lambda has finished. */
    @GetMapping
    public CoverMetadataResponse getCover(@PathVariable Long id) {
        return coverService.findMetadata(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cover metadata not found for book " + id + " (upload may still be processing)"));
    }
}
