package org.restless.iwkbackoffice.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Range<T> {
    private T from;
    private T to;
}
