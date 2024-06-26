package ru.iopump.qa.allure.api.youtrack;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "youtrack-issues", url = "${tms.api-base-url}")
public interface IssuesClient extends org.brewcode.api.youtrack.IssuesApi {
}
