package org.restless.iwkbackoffice.resource;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.*;
import org.restless.iwkbackoffice.service.ImportService;
import org.restless.iwkbackoffice.service.OrderService;
import org.restless.iwkbackoffice.util.Range;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrdersResource {
    private final ImportService importService;
    private final OrderService orderService;

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @PostMapping(
            path = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OrdersImportResponse> importOrders(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(importService.importOrders(file));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OrderCreateResponse> createOrder(@RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/filterlimits")
    public ResponseEntity<FilterLimits> getFilterLimits() {
        return ResponseEntity.ok(orderService.getFilterLimits());
    }

    @GetMapping
    public ResponseEntity<PageData<OrderResponse>> findOrders(
            @RequestParam int page, @RequestParam int perPage,
            @RequestParam String query, @RequestParam String date,
            @RequestParam String subTotal, @RequestParam String taxAmount,
            @RequestParam String taxRate, @RequestParam String totalAmount
    ) {
        String[] dates = date.split(",", -1);
        LocalDate dateFrom = parseDateBound(dates, 0);
        LocalDate dateTo = parseDateBound(dates, 1);

        Double[] subTotals = Arrays.stream(subTotal.split(",")).map(Double::valueOf).toArray(Double[]::new);
        Double[] taxAmounts = Arrays.stream(taxAmount.split(",")).map(Double::valueOf).toArray(Double[]::new);
        Double[] taxRates = Arrays.stream(taxRate.split(",")).map(Double::valueOf).toArray(Double[]::new);
        Double[] totalAmounts = Arrays.stream(totalAmount.split(",")).map(Double::valueOf).toArray(Double[]::new);

        OrdersFilter ordersFilter = new OrdersFilter(
                query, new Range<>(dateFrom, dateTo),
                new Range<>(subTotals[0], subTotals[1]), new Range<>(taxAmounts[0], taxAmounts[1]),
                new Range<>(taxRates[0], taxRates[1]), new Range<>(totalAmounts[0], totalAmounts[1])
        );

        PageData<OrderResponse> orders = orderService.findOrders(ordersFilter, page, perPage);

        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    private ResponseEntity<OrderDetailsResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    private LocalDate parseDateBound(String[] dates, int index) {
        if (dates.length <= index || dates[index].isBlank()) {
            return null;
        }

        return LocalDate.parse(dates[index], FORMAT);
    }
}
