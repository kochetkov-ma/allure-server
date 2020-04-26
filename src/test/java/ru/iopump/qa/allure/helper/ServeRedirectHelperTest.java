package ru.iopump.qa.allure.helper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@WebMvcTest
public class ServeRedirectHelperTest {

    @Autowired
    private ServeRedirectHelper serveHelper;

    @Test
    public void redirectWithUsingRedirectView() {
    }

    @Test
    public void mapRequestTo() {
        serveHelper.mapRequestTo("/allure/reports/test", "/allure/reports/532a2f82-745e-42da-b6dd-2765924ec792");
    }
}