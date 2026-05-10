package com.toy.reservationlab.common.component;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final Object[] args;

    public BizException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BizException(ErrorCode errorCode, Object[] args) {
        super(errorCode.getCode());
        this.code = errorCode.getCode();
        this.args = args;
    }
}
