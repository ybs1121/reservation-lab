package com.toy.reservationlab.common.component;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final Object[] args;

    public BizException(String code) {
        this(code, null);
    }

    public BizException(String code, Object[] args) {
        super(code);
        this.code = code;
        this.args = args;
    }
}

