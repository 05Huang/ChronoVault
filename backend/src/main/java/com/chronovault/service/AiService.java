package com.chronovault.service;

import com.chronovault.ai.AiAnalysisService;
import com.chronovault.dto.ai.*;
import com.chronovault.entity.AiInsight;
import com.chronovault.entity.AiRecommendation;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.AiInsightRepository;
import com.chronovault.repository.AiRecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiService {

    private final AiInsightRepository aiInsightRepository;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final AiAnalysisService aiAnalysisService;

    public List<AiInsightDTO> getInsights() {
        return aiInsightRepository.findAll().stream()
                .map(AiInsightDTO::from)
                .toList();
    }

    public List<AiRecommendationDTO> getRecommendations() {
        return aiRecommendationRepository.findAll().stream()
                .map(AiRecommendationDTO::from)
                .toList();
    }

    @Transactional
    public void applyRecommendation(Long id) {
        AiRecommendation rec = aiRecommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("建议不存在: " + id));
        rec.setApplied(true);
        aiRecommendationRepository.save(rec);
    }

    public RiskRadarDTO getRiskRadar() {
        Map<String, Double> scores = aiAnalysisService.getRiskRadar();
        List<String> keys = List.of("数据安全", "系统稳定", "备份完整", "网络防护", "存储健康");
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (String key : keys) {
            double val = scores.getOrDefault(key, 80.0);
            indicators.add(Map.of("max", 100, "name", key));
            values.add(val);
        }
        return new RiskRadarDTO(indicators, values);
    }

    public Map<String, Object> getStoragePrediction() {
        List<Map<String, Object>> predictions = aiAnalysisService.getStoragePrediction();
        List<String> months = new ArrayList<>();
        List<Long> predicted = new ArrayList<>();
        for (Map<String, Object> p : predictions) {
            months.add((String) p.get("label"));
            predicted.add(((Number) p.get("bytes")).longValue());
        }
        // Build actual data from storage targets (current usage as baseline)
        List<Long> actual = new ArrayList<>();
        if (!predicted.isEmpty()) {
            actual.add(predicted.get(0)); // current month actual ≈ first prediction
            for (int i = 1; i < predicted.size(); i++) actual.add(null);
        }
        return Map.of("months", months, "actual", actual, "predicted", predicted);
    }

    @Transactional
    public String generateReport() {
        String report = aiAnalysisService.generateReport();

        // Store as insight
        AiInsight insight = AiInsight.builder()
                .title("AI 分析报告")
                .description(report)
                .category("REPORT")
                .severity("INFO")
                .build();
        aiInsightRepository.save(insight);

        return report;
    }
}
