package com.stockmate.stock;

import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockmate.common.PageResponse;
import com.stockmate.stock.StockDtos.AdjustmentRequest;
import com.stockmate.stock.StockDtos.MovementResponse;

/**
 * Stock movement history and manual adjustments.
 * سجل حركات المخزون والتسويات اليدوية.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    // Newest first by default; "id" breaks ties between movements saved in the same instant, so paging never
    // repeats or skips a row. "from" is inclusive and "to" is exclusive (ISO-8601 instants).
    // الأحدث أولًا افتراضيًا؛ و"id" يفصل بين الحركات المحفوظة بنفس اللحظة، حتى التنقل بين الصفحات ما يكرر أو يتخطى صف.
    // "from" شامل و"to" غير شامل (بصيغة ISO-8601).
    @GetMapping("/movements")
    public PageResponse<MovementResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(productId, type, from, to, pageable);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        return service.adjust(request);
    }
}
