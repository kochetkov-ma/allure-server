package ru.iopump.qa.allure.gui;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import ru.iopump.qa.allure.AppCfg;

@Slf4j
public class DateTimeResolverTest {

    private DateTimeResolver resolverSpy;

    @Before
    public void setUp() throws Exception {

        var resolver = new DateTimeResolver(new AppCfg().dateFormat("yy/MM/dd HH:mm:ss"));
        var formatterInCurrentTimeZone = DateTimeFormatter.ofPattern("yy/MM/dd HH:mm:ss").withZone(TimeZone.getDefault().toZoneId());

        resolverSpy = Mockito.spy(resolver);
        Mockito.when(resolverSpy.acquireFormatter()).thenReturn(formatterInCurrentTimeZone);
    }

    @Test
    public void printDate() {
        var dateInZeroTimeZone = LocalDateTime.now(ZoneOffset.UTC);

        String res = resolverSpy.printDate(dateInZeroTimeZone);
        log.info(res);

    }
}