package ru.iopump.qa.allure.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilTest {

    @Test
    public void lastSegment() {
        assertEquals(Util.lastSegment("http://localhost:8080/allure/reports/e6b22402-a153-4eac-9254-fae06a232931/"),
            "e6b22402-a153-4eac-9254-fae06a232931");
    }
}