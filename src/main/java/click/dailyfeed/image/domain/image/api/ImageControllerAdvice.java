package click.dailyfeed.image.domain.image.api;

import click.dailyfeed.code.domain.image.exception.ImageException;
import click.dailyfeed.code.global.web.code.ResponseSuccessCode;
import click.dailyfeed.code.global.web.response.DailyfeedErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "click.dailyfeed.image.domain.image.api")
public class ImageControllerAdvice {
    @ExceptionHandler(ImageException.class)
    public DailyfeedErrorResponse handleImageException(
            ImageException e,
            HttpServletRequest request) {

        log.warn("Comment exception occurred: {}, path: {}",
                e.getImageExceptionCode().getMessage(),
                request.getRequestURI());

        return DailyfeedErrorResponse.of(
                e.getImageExceptionCode().getStatusCode(),
                ResponseSuccessCode.FAIL,
                e.getImageExceptionCode().getMessage(),
                request.getRequestURI()
        );
    }

    // 일반적인 RuntimeException 처리 (예상치 못한 오류)
    @ExceptionHandler(RuntimeException.class)
    public DailyfeedErrorResponse handleRuntimeException(
            RuntimeException e,
            HttpServletRequest request) {

        log.error("Unexpected runtime exception occurred", e);

        return DailyfeedErrorResponse.of(
                500,
                ResponseSuccessCode.FAIL,
                "서버 내부 오류가 발생했습니다.",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public DailyfeedErrorResponse handleException(
            Exception e,
            HttpServletRequest request
    ){
        log.error("Unexpected exception occurred", e);

        return DailyfeedErrorResponse.of(
                500,
                ResponseSuccessCode.FAIL,
                "서버 내부 오류가 발생했습니다.",
                request.getRequestURI()
        );
    }
}
