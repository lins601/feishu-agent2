package com.example.myapp.service;

import com.example.myapp.dto.WiseFaqIngestionResult;
import com.example.myapp.model.FaqDraftRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FAQ 草稿闭环：
 * 未命中 / 负反馈 → FAQ 草稿 → 管理员确认 → WISE FAQ 入库。
 */
@Service
public class FaqDraftService {

    private static final Logger log = LoggerFactory.getLogger(FaqDraftService.class);

    private final LarkBitableService bitableService;
    private final WiseFaqService wiseFaqService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${faq.draft.enabled:true}")
    private boolean enabled;

    @Value("${faq.draft.ingest-enabled:true}")
    private boolean ingestEnabled;

    @Value("${faq.draft.miss-threshold:3}")
    private int missThreshold;

    public FaqDraftService(LarkBitableService bitableService, WiseFaqService wiseFaqService) {
        this.bitableService = bitableService;
        this.wiseFaqService = wiseFaqService;
    }

    @Scheduled(initialDelayString = "${faq.draft.reconcile-initial-delay-ms:15000}",
            fixedDelayString = "${faq.draft.reconcile-interval-ms:300000}")
    public void reconcileFaqDrafts() {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("FAQ 草稿闭环任务仍在运行，本轮跳过");
            return;
        }

        try {
            List<FaqDraftRecord> missDrafts =
                    bitableService.createFaqDraftsFromMissQuestions(missThreshold);
            List<FaqDraftRecord> reviewDrafts =
                    bitableService.createFaqDraftsFromReviewTasks();
            if (!missDrafts.isEmpty() || !reviewDrafts.isEmpty()) {
                log.info("FAQ 草稿生成完成: miss={}, review={}",
                        missDrafts.size(), reviewDrafts.size());
            }

            if (ingestEnabled) {
                ingestReadyDrafts();
            }
        } catch (Exception e) {
            log.warn("FAQ 草稿闭环任务失败", e);
        } finally {
            running.set(false);
        }
    }

    void ingestReadyDrafts() {
        List<FaqDraftRecord> drafts = bitableService.listFaqDraftsReadyForIngestion();
        if (drafts.isEmpty()) {
            return;
        }

        log.info("发现待入库 FAQ 草稿: {} 条", drafts.size());
        for (FaqDraftRecord draft : drafts) {
            WiseFaqIngestionResult result = wiseFaqService.createFaq(draft);
            if (result.isSuccess()) {
                bitableService.updateFaqDraftIngestionResult(
                        draft.getRecordId(), "已入库", result.getFaqId(), "");
            } else {
                bitableService.updateFaqDraftIngestionResult(
                        draft.getRecordId(), "入库失败", "",
                        result.getErrorMessage());
            }
        }
    }
}
