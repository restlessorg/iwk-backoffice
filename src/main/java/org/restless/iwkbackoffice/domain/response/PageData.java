package org.restless.iwkbackoffice.domain.response;

import java.util.List;

public record PageData<T>(List<T> data, int totalElements) {
}
