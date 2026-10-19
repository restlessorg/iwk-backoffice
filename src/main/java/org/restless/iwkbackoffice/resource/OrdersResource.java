package org.restless.iwkbackoffice.resource;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderCreateResponse;
import org.restless.iwkbackoffice.domain.response.OrderDetailsResponse;
import org.restless.iwkbackoffice.domain.response.OrderLocation;
import org.restless.iwkbackoffice.domain.response.OrderResponse;
import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.restless.iwkbackoffice.domain.response.PageData;
import org.restless.iwkbackoffice.service.ImportService;
import org.restless.iwkbackoffice.service.OrderService;
import org.restless.iwkbackoffice.service.OrdersFilterParser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrdersResource {
    private final ImportService importService;
    private final OrderService orderService;
    private final OrdersFilterParser ordersFilterParser;

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
    public ResponseEntity<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/filterlimits")
    public ResponseEntity<FilterLimits> getFilterLimits() {
        return ResponseEntity.ok(orderService.getFilterLimits());
    }

    @GetMapping
    public ResponseEntity<PageData<OrderResponse>> findOrders(
            @RequestParam int page, @RequestParam int perPage,
            @RequestParam(required = false) String query, @RequestParam(required = false) String date,
            @RequestParam(required = false) String subTotal, @RequestParam(required = false) String taxAmount,
            @RequestParam(required = false) String taxRate, @RequestParam(required = false) String totalAmount
    ) {
        ordersFilterParser.validatePagination(page, perPage);
        OrdersFilter ordersFilter = ordersFilterParser.parse(query, date, subTotal, taxAmount, taxRate, totalAmount);
        return ResponseEntity.ok(orderService.findOrders(ordersFilter, page, perPage));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailsResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping("/map")
    public ResponseEntity<List<OrderLocation>> getOrdersLocations() {
        return ResponseEntity.ok(orderService.getOrdersLocations());
    }
}
