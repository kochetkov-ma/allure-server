package ru.iopump.qa.allure.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReportResponse {
    String path;
    String url;

    public ReportResponse(String path, String baseUrl) {
        this.path = path;
        this.url = baseUrl + path + "/" + "index.html";
    }
}
