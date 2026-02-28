package org.restless.iwkbackoffice.resource;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderCreateResponse;
import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.restless.iwkbackoffice.service.ImportService;
import org.restless.iwkbackoffice.service.OrderService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrdersResource {
    private final ImportService importService;
    private final OrderService orderService;

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
}
