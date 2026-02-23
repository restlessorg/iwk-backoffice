package org.restless.iwkbackoffice.service;

import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImportService {
    OrdersImportResponse importOrders(MultipartFile file);
}
