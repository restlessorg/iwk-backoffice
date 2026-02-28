package org.restless.iwkbackoffice.domain.response;

import lombok.Data;

import java.util.List;

@Data
public class PageData<T> {
    private List<T> data;
    private int totalElements;
}
