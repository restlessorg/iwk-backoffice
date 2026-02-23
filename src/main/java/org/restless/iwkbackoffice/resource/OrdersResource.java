package org.restless.iwkbackoffice.resource;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.restless.iwkbackoffice.service.ImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrdersResource {
    private final ImportService importService;

    @PostMapping(
            path = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OrdersImportResponse> importOrders(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(importService.importOrders(file));
    }
}
