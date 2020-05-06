package ru.iopump.qa.allure.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultResponse {
    /**
     * Internal filename as {@link UUID}.
     */
    String uuid;
    /**
     * Directory size in KB.
     */
    long size;
    /**
     * Created Date Time.
     */
    LocalDateTime created;
}
