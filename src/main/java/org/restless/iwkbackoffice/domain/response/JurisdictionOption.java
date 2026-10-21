package org.restless.iwkbackoffice.domain.response;

import org.restless.iwkbackoffice.domain.tax.JurisdictionType;

public record JurisdictionOption(Long id, String name, JurisdictionType type) {
}
