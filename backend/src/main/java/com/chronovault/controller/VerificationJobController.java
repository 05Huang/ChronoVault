package com.chronovault.controller;

import com.chronovault.entity.VerificationJob;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.VerificationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/verification-jobs")
@RequiredArgsConstructor
public class VerificationJobController {

    private final VerificationJobService jobService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VerificationJob>>> getJobs() {
        return ResponseEntity.ok(ApiResponse.success(jobService.getJobs()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VerificationJob>> createJob(@RequestBody VerificationJob job) {
        return ResponseEntity.ok(ApiResponse.success(jobService.createJob(job)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VerificationJob>> updateJob(
            @PathVariable Long id,
            @RequestBody VerificationJob job) {
        return ResponseEntity.ok(ApiResponse.success(jobService.updateJob(id, job)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.successMsg("验证任务已删除"));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<ApiResponse<VerificationJob>> runJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobService.runJob(id)));
    }
}