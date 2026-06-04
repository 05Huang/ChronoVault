package com.chronovault.controller;

import com.chronovault.entity.VerificationJob;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.VerificationJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import com.chronovault.config.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(ApiVersion.V1 + "/verification-jobs")
@RequiredArgsConstructor
public class VerificationJobController {

    private final VerificationJobService jobService;

    @Operation(summary = "获取 Jobs")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VerificationJob>>> getJobs() {
        return ResponseEntity.ok(ApiResponse.success(jobService.getJobs()));
    }

    @Operation(summary = "操作 Job")
    @PostMapping
    public ResponseEntity<ApiResponse<VerificationJob>> createJob(@RequestBody VerificationJob job) {
        VerificationJob created = jobService.createJob(job);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "verification-jobs/" + created.getId()))
                .body(ApiResponse.success(created));
    }

    @Operation(summary = "更新 Job")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VerificationJob>> updateJob(
            @PathVariable Long id,
            @RequestBody VerificationJob job) {
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "verification-jobs/" + id))
                .body(ApiResponse.success(jobService.updateJob(id, job)));
    }

    @Operation(summary = "删除 Job")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.successMsg("验证任务已删除"));
    }

    @Operation(summary = "操作 Job")
    @PostMapping("/{id}/run")
    public ResponseEntity<ApiResponse<VerificationJob>> runJob(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(jobService.runJob(id)));
    }
}